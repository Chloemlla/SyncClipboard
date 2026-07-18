namespace SyncClipboard.Core.Models;

public record class OpenSourceSoftware(
    string Name,
    string HomePage,
    string LicensePath,
    string Author = "",
    string Description = "",
    string LicenseName = "")
{
    public bool IsValidLicensePath => !string.IsNullOrWhiteSpace(LicensePath);
}
