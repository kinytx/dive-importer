using System.Windows;

namespace DivePlanImporter.Windows;

public partial class MainWindow : Window
{
    private readonly BleAdvertisementScanner scanner;

    public MainWindow()
    {
        InitializeComponent();

        var detector = DeviceRuleDetector.LoadDefault();
        scanner = new BleAdvertisementScanner(detector);
        scanner.DevicesChanged += devices => Dispatcher.Invoke(() =>
        {
            DevicesList.ItemsSource = devices;
            StatusText.Text = $"已发现 {devices.Count} 台设备";
        });
        scanner.StatusChanged += message => Dispatcher.Invoke(() =>
        {
            StatusText.Text = message;
            ScanButton.Content = scanner.IsScanning ? "停止扫描" : "开始扫描";
        });
    }

    private void ScanButton_Click(object sender, RoutedEventArgs e)
    {
        if (scanner.IsScanning)
        {
            scanner.Stop();
            ScanButton.Content = "开始扫描";
            return;
        }

        scanner.Start();
        ScanButton.Content = "停止扫描";
    }

    protected override void OnClosed(EventArgs e)
    {
        scanner.Dispose();
        base.OnClosed(e);
    }
}
