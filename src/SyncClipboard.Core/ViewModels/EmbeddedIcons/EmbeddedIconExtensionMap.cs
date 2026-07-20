using System.Collections.Frozen;

namespace SyncClipboard.Core.ViewModels.EmbeddedIcons;

internal static class EmbeddedIconExtensionMap
{
    private static readonly string[] FontExtensions = [
        ".ttf", ".otf", ".ttc", ".otc", ".woff", ".woff2", ".eot", ".dfont", ".fon", ".fnt",
    ];
    private static readonly string[] KeyFileExtensions = [
        ".pem", ".crt", ".cer", ".der", ".key", ".pfx", ".p12", ".p7b", ".p7c", ".csr", ".asc", ".gpg", ".pgp",
        ".mobileprovision",
    ];
    private static readonly string[] SubtitleExtensions = [
        ".srt", ".ass", ".ssa", ".vtt", ".sub", ".idx", ".scc", ".ttml", ".dfxp", ".lrc",
        ".sbv", ".smi", ".sami", ".sup",
    ];
    private static readonly string[] DiskImageExtensions = [
        ".iso", ".img", ".dmg", ".sparseimage", ".sparsebundle", ".vhd", ".vhdx", ".vmdk", ".qcow", ".qcow2", ".vdi",
        ".hdd", ".hds", ".ova", ".ovf", ".wim", ".esd", ".toast", ".cdr", ".udif",
    ];
    private static readonly string[] AiModelExtensions = [
        ".safetensors", ".safesensers", ".ckpt", ".gguf", ".ggml", ".onnx", ".tflite", ".mlmodel",
        ".engine", ".trt", ".h5", ".keras", ".pth", ".pt", ".pdparams", ".mindir", ".mnn", ".rknn", ".ncnn", ".lora",
    ];
    private static readonly string[] BinaryExtensions = [
        ".bin", ".dat", ".obj", ".o", ".a", ".lib", ".dll", ".exe", ".msi", ".sys", ".drv",
        ".so", ".dylib", ".class", ".pyc", ".pyo", ".wasm", ".pdb",
    ];
    private static readonly string[] TextFileExtensions = [
        ".txt", ".text", ".md", ".markdown", ".mdown", ".mkdn", ".rtf", ".log", ".nfo",
        ".ini", ".cfg", ".conf", ".properties", ".readme", ".changelog", ".pdf", ".doc",
        ".docs", ".docx", ".docm", ".dot", ".dotx", ".dotm", ".odt", ".ott", ".pages",
        ".epub", ".mobi", ".azw", ".azw3",
    ];
    private static readonly string[] SpreadsheetExtensions = [
        ".xls", ".xlsx", ".xlsm", ".xlsb", ".xlt", ".xltx", ".xltm", ".csv", ".tsv", ".ods", ".fods",
    ];
    private static readonly string[] PresentationExtensions = [
        ".ppt", ".pptx", ".pptm", ".pps", ".ppsx", ".ppsm", ".pot", ".potx", ".potm", ".odp", ".otp", ".key",
    ];
    private static readonly string[] ArchiveExtensions = [
        ".zip", ".zipx", ".rar", ".7z", ".tar", ".gz", ".gzip", ".bz2", ".xz", ".zst", ".lz",
        ".lzma", ".cab", ".jar", ".war", ".ear", ".apk", ".xpi",
    ];
    private static readonly string[] DatabaseExtensions = [
        ".db", ".sqlite", ".sqlite3", ".db3", ".sdb", ".mdb", ".accdb", ".dbf", ".duckdb", ".parquet",
    ];
    private static readonly string[] CodeExtensions = [
        ".cs", ".csx", ".vb", ".fs", ".fsx", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".vue",
        ".svelte", ".py", ".pyw", ".java", ".kt", ".kts", ".go", ".rs", ".rb", ".php", ".swift", ".dart", ".scala",
        ".c", ".h", ".cc", ".cp", ".cpp", ".cxx", ".hxx", ".hpp", ".m", ".mm", ".sh", ".bash", ".zsh", ".fish",
        ".ps1", ".psm1", ".psd1", ".bat", ".cmd", ".html", ".htm", ".xhtml", ".css", ".scss", ".sass", ".less",
        ".xml", ".xsl", ".xslt", ".json", ".jsonc", ".json5", ".yml", ".yaml", ".toml", ".sql", ".graphql", ".gql",
        ".proto", ".gradle", ".cmake",
    ];
    private static readonly string[] AudioExtensions = [
        ".mp3", ".wav", ".flac", ".aac", ".m4a", ".ogg", ".oga", ".opus", ".wma", ".aiff", ".aif", ".amr",
        ".ape", ".mid", ".midi",
    ];
    private static readonly string[] VideoExtensions = [
        ".mp4", ".m4v", ".mkv", ".avi", ".mov", ".wmv", ".webm", ".flv", ".f4v", ".3gp", ".3g2", ".mpeg",
        ".mpg", ".ts", ".m2ts", ".mts", ".asf", ".rm", ".rmvb", ".vob", ".dv", ".mxf", ".m2v", ".m2p", ".hevc", ".ogv",
    ];
    private static readonly string[] ImageFileExtensions = [
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".tif", ".tiff", ".ico", ".avif", ".heic", ".heif",
        ".jxl", ".raw", ".cr2", ".nef", ".arw", ".psd",
    ];
    private static readonly string[] ThreeDimensionalExtensions = [
        ".fbx", ".stl", ".blend", ".3ds", ".gltf", ".glb", ".dae", ".usd", ".usda", ".usdc", ".usdz", ".ply",
        ".step", ".stp", ".iges", ".igs", ".3mf", ".wrl", ".vrml", ".x3d",
    ];

    private static readonly FrozenDictionary<string, EmbeddedIconCategory> ExtensionCategories =
        CreateExtensionCategories();

    public static bool TryResolve(string extension, out EmbeddedIconCategory category) =>
        ExtensionCategories.TryGetValue(extension, out category);

    private static FrozenDictionary<string, EmbeddedIconCategory> CreateExtensionCategories()
    {
        var categories = new Dictionary<string, EmbeddedIconCategory>(StringComparer.OrdinalIgnoreCase);
        AddExtensions(categories, AiModelExtensions, EmbeddedIconCategory.AiModel);
        AddExtensions(categories, FontExtensions, EmbeddedIconCategory.Font);
        AddExtensions(categories, KeyFileExtensions, EmbeddedIconCategory.KeyFile);
        AddExtensions(categories, SubtitleExtensions, EmbeddedIconCategory.Subtitle);
        AddExtensions(categories, DiskImageExtensions, EmbeddedIconCategory.DiskImage);
        AddExtensions(categories, BinaryExtensions, EmbeddedIconCategory.Binary);
        AddExtensions(categories, TextFileExtensions, EmbeddedIconCategory.TextFile);
        AddExtensions(categories, SpreadsheetExtensions, EmbeddedIconCategory.Spreadsheet);
        AddExtensions(categories, PresentationExtensions, EmbeddedIconCategory.Presentation);
        AddExtensions(categories, ArchiveExtensions, EmbeddedIconCategory.Archive);
        AddExtensions(categories, DatabaseExtensions, EmbeddedIconCategory.Database);
        AddExtensions(categories, CodeExtensions, EmbeddedIconCategory.Code);
        AddExtensions(categories, AudioExtensions, EmbeddedIconCategory.Audio);
        AddExtensions(categories, VideoExtensions, EmbeddedIconCategory.Video);
        AddExtensions(categories, ImageFileExtensions, EmbeddedIconCategory.ImageFile);
        AddExtensions(categories, ThreeDimensionalExtensions, EmbeddedIconCategory.ThreeDimensional);
        return categories.ToFrozenDictionary(StringComparer.OrdinalIgnoreCase);
    }

    private static void AddExtensions(
        Dictionary<string, EmbeddedIconCategory> categories,
        IEnumerable<string> extensions,
        EmbeddedIconCategory category)
    {
        foreach (var extension in extensions)
        {
            categories.TryAdd(extension, category);
        }
    }
}
