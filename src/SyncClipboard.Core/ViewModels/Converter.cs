using SyncClipboard.Core.I18n;
using SyncClipboard.Core.ViewModels.Sub;
using SyncClipboard.Shared.Profiles;

namespace SyncClipboard.Core.ViewModels;

public class Converter
{
    public static string ProfileTypeToLocalizedString(ProfileType type)
    {
        return type switch
        {
            ProfileType.Text => Strings.HistoryFilterText,
            ProfileType.Image => Strings.HistoryFilterImage,
            ProfileType.File => Strings.HistoryFilterFile,
            ProfileType.Group => Strings.HistoryFilterFile,
            _ => string.Empty
        };
    }

    public static string GetRecordSize(HistoryRecordVM? record)
    {
        if (record == null)
            return string.Empty;

        if (record.Type == ProfileType.Text)
        {
            // 文字类型：显示字符数，不加单位
            return record.Size.ToString();
        }
        else
        {
            // 其他类型：显示文件大小格式
            return FormatFileSize(record.Size);
        }
    }

    private static string FormatFileSize(long size)
    {
        string[] units = ["B", "KB", "MB", "GB", "TB"];
        double len = size;
        int unitIndex = 0;

        while (len >= 1024 && unitIndex < units.Length - 1)
        {
            len /= 1024;
            unitIndex++;
        }

        return $"{len:0.#} {units[unitIndex]}";
    }

    public static string ServiceStatusToFontIcon(bool isError)
    {
        return isError ? "\uE10A" : "\uE17B";
    }

    public static string BoolToPasswordFontIcon(bool show)
    {
        return show ? "\uF78D" : "\uED1A";
    }

    public static string LimitUIText(string input)
    {
        const int MAX_LINES = 10;
        const int MAX_LINE_LENGTH = 500;
        if (input is null)
        {
            return string.Empty;
        }
        var lines = input.Split(["\r\n", "\r", "\n"], StringSplitOptions.None)
            .Take(11)
            .Select(line => line.Length > MAX_LINE_LENGTH ? line[..MAX_LINE_LENGTH] + "..." : line)
            .ToArray();
        if (lines.Length == MAX_LINES + 1)
        {
            lines[MAX_LINES] = "...";
        }
        input = string.Join(Environment.NewLine, lines);
        return input;
    }

    public static string LimitHistoryListText(string? input, bool isCompactListMode)
    {
        if (input is null)
        {
            return string.Empty;
        }

        if (!isCompactListMode)
        {
            return LimitUIText(input);
        }

        var firstContentLine = input
            .Split(["\r\n", "\r", "\n"], StringSplitOptions.None)
            .FirstOrDefault(line => !string.IsNullOrWhiteSpace(line));

        return firstContentLine is null ? string.Empty : LimitUIText(firstContentLine);
    }
}
