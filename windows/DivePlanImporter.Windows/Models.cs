namespace DivePlanImporter.Windows;

public sealed record DeviceRuleSet(
    int SchemaVersion,
    IReadOnlyList<ServiceUuidHint> ServiceUuidHints,
    IReadOnlyList<DeviceNamePatternRule> DeviceNamePatterns);

public sealed record ServiceUuidHint(
    string Vendor,
    IReadOnlyList<string> Uuids,
    bool Weak = false);

public sealed record DeviceNamePatternRule(
    string Pattern,
    string Vendor,
    string Product,
    bool Ambiguous = false,
    bool Weak = false,
    string? Hint = null);

public sealed record VendorMatch(
    bool IsHit,
    string Vendor,
    string Product,
    bool Ambiguous,
    bool Weak,
    string? Hint,
    string Source)
{
    public static VendorMatch Unknown { get; } =
        new(false, "未识别", "(未知型号)", false, false, null, "unknown");
}

public sealed class DiscoveredDevice
{
    public ulong BluetoothAddress { get; init; }
    public string AddressText => BluetoothAddress.ToString("X12");
    public string Name { get; set; } = "(无名称)";
    public int Rssi { get; set; }
    public IReadOnlyList<Guid> ServiceUuids { get; set; } = Array.Empty<Guid>();
    public VendorMatch Match { get; set; } = VendorMatch.Unknown;
    public DateTimeOffset FirstSeenAt { get; init; } = DateTimeOffset.Now;
    public DateTimeOffset LastSeenAt { get; set; } = DateTimeOffset.Now;
    public string DisplayModel => Match.IsHit ? $"{Match.Vendor} · {Match.Product}" : "未识别设备";
    public string Detail => $"{AddressText} · {Rssi} dBm · {Match.Source}";
    public string Hint => Match.Hint ?? (Match.Weak ? "需要特殊导入路径" : "");
}
