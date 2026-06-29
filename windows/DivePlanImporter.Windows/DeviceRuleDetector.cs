using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace DivePlanImporter.Windows;

public sealed class DeviceRuleDetector
{
    private readonly Dictionary<Guid, ServiceUuidHint> serviceHints;
    private readonly List<(Regex Regex, DeviceNamePatternRule Rule)> namePatterns;

    private DeviceRuleDetector(DeviceRuleSet rules)
    {
        serviceHints = rules.ServiceUuidHints
            .SelectMany(hint => hint.Uuids.Select(uuid => (Uuid: Guid.Parse(uuid), Hint: hint)))
            .ToDictionary(pair => pair.Uuid, pair => pair.Hint);

        namePatterns = rules.DeviceNamePatterns
            .Select(rule => (
                Regex: new Regex(rule.Pattern, RegexOptions.IgnoreCase | RegexOptions.CultureInvariant),
                Rule: rule))
            .ToList();
    }

    public static DeviceRuleDetector LoadDefault()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "Rules", "device-rules.json");
        var json = File.ReadAllText(path);
        var options = new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true,
            ReadCommentHandling = JsonCommentHandling.Skip,
        };
        options.Converters.Add(new JsonStringEnumConverter());

        var rules = JsonSerializer.Deserialize<DeviceRuleSet>(json, options)
            ?? throw new InvalidOperationException("无法读取设备识别规则");
        return new DeviceRuleDetector(rules);
    }

    public VendorMatch Detect(string? advertisedName, IReadOnlyList<Guid> serviceUuids)
    {
        foreach (var uuid in serviceUuids)
        {
            if (!serviceHints.TryGetValue(uuid, out var serviceHint))
            {
                continue;
            }

            var productHit = DetectByName(advertisedName);
            if (productHit.IsHit &&
                string.Equals(productHit.Vendor, serviceHint.Vendor, StringComparison.OrdinalIgnoreCase))
            {
                return productHit with { Source = "service-uuid+name" };
            }

            return new VendorMatch(
                IsHit: true,
                Vendor: serviceHint.Vendor,
                Product: "(未确定型号)",
                Ambiguous: true,
                Weak: serviceHint.Weak,
                Hint: null,
                Source: "service-uuid");
        }

        return DetectByName(advertisedName);
    }

    private VendorMatch DetectByName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
        {
            return VendorMatch.Unknown;
        }

        foreach (var (regex, rule) in namePatterns)
        {
            if (!regex.IsMatch(name))
            {
                continue;
            }

            return new VendorMatch(
                IsHit: true,
                Vendor: rule.Vendor,
                Product: rule.Product,
                Ambiguous: rule.Ambiguous,
                Weak: rule.Weak,
                Hint: rule.Hint,
                Source: "device-name");
        }

        return VendorMatch.Unknown;
    }
}
