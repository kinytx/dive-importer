using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;

namespace DivePlanImporter.Windows;

public sealed class BleAdvertisementScanner : IDisposable
{
    private readonly BluetoothLEAdvertisementWatcher watcher;
    private readonly DeviceRuleDetector detector;
    private readonly Dictionary<ulong, DiscoveredDevice> devices = new();
    private readonly object gate = new();

    public BleAdvertisementScanner(DeviceRuleDetector detector)
    {
        this.detector = detector;
        watcher = new BluetoothLEAdvertisementWatcher
        {
            ScanningMode = BluetoothLEScanningMode.Active,
        };
        watcher.Received += OnReceived;
        watcher.Stopped += OnStopped;
    }

    public event Action<IReadOnlyList<DiscoveredDevice>>? DevicesChanged;
    public event Action<string>? StatusChanged;

    public bool IsScanning => watcher.Status is BluetoothLEAdvertisementWatcherStatus.Started;

    public void Start()
    {
        lock (gate)
        {
            devices.Clear();
        }
        StatusChanged?.Invoke("正在扫描 BLE 广播...");
        watcher.Start();
    }

    public void Stop()
    {
        if (IsScanning)
        {
            watcher.Stop();
        }
    }

    private void OnReceived(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementReceivedEventArgs args)
    {
        var name = string.IsNullOrWhiteSpace(args.Advertisement.LocalName)
            ? "(无名称)"
            : args.Advertisement.LocalName;
        var serviceUuids = args.Advertisement.ServiceUuids.ToArray();
        var now = DateTimeOffset.Now;
        var match = detector.Detect(name, serviceUuids);

        List<DiscoveredDevice> ordered;
        lock (gate)
        {
            if (!devices.TryGetValue(args.BluetoothAddress, out var device))
            {
                device = new DiscoveredDevice
                {
                    BluetoothAddress = args.BluetoothAddress,
                    FirstSeenAt = now,
                };
                devices[args.BluetoothAddress] = device;
            }

            device.Name = name;
            device.Rssi = args.RawSignalStrengthInDBm;
            device.ServiceUuids = serviceUuids;
            device.Match = match;
            device.LastSeenAt = now;

            ordered = devices.Values
                .OrderByDescending(device => device.Match.IsHit)
                .ThenByDescending(device => device.Rssi)
                .ToList();
        }

        DevicesChanged?.Invoke(ordered);
    }

    private void OnStopped(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementWatcherStoppedEventArgs args)
    {
        var message = args.Error == BluetoothError.Success
            ? "扫描已停止"
            : $"扫描停止：{args.Error}";
        StatusChanged?.Invoke(message);
    }

    public void Dispose()
    {
        watcher.Received -= OnReceived;
        watcher.Stopped -= OnStopped;
        Stop();
    }
}
