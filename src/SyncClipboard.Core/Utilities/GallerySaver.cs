using SyncClipboard.Shared.Profiles;
using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;

namespace SyncClipboard.Core.Utilities;

/// <summary>
/// Saves downloaded images into the user's Pictures/SyncClipboard album,
/// mirroring the Android GallerySaver behavior.
/// </summary>
public static class GallerySaver
{
    private const string AlbumFolderName = "SyncClipboard";

    public static async Task<string?> SaveImageAsync(string? sourcePath, string? preferredFileName = null, CancellationToken token = default)
    {
        if (string.IsNullOrWhiteSpace(sourcePath) || !File.Exists(sourcePath))
        {
            return null;
        }

        var pictures = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures);
        if (string.IsNullOrWhiteSpace(pictures))
        {
            pictures = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        }

        var albumDir = Path.Combine(pictures, AlbumFolderName);
        Directory.CreateDirectory(albumDir);

        var fileName = Path.GetFileName(string.IsNullOrWhiteSpace(preferredFileName) ? sourcePath : preferredFileName);
        if (string.IsNullOrWhiteSpace(fileName))
        {
            fileName = ImageProfile.CreateImageFileName();
        }

        foreach (var c in Path.GetInvalidFileNameChars())
        {
            fileName = fileName.Replace(c, '_');
        }

        var targetPath = GetUniquePath(albumDir, fileName);
        await using var source = new FileStream(sourcePath, FileMode.Open, FileAccess.Read, FileShare.Read);
        await using var target = new FileStream(targetPath, FileMode.CreateNew, FileAccess.Write, FileShare.None);
        await source.CopyToAsync(target, token);
        return targetPath;
    }

    private static string GetUniquePath(string directory, string fileName)
    {
        var candidate = Path.Combine(directory, fileName);
        if (!File.Exists(candidate))
        {
            return candidate;
        }

        var stem = Path.GetFileNameWithoutExtension(fileName);
        var ext = Path.GetExtension(fileName);
        var index = 1;
        while (true)
        {
            candidate = Path.Combine(directory, $"{stem}_{index}{ext}");
            if (!File.Exists(candidate))
            {
                return candidate;
            }
            index++;
        }
    }
}
