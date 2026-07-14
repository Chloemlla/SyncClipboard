using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Imaging;
using SyncClipboard.Core.Models;
using SyncClipboard.Core.ViewModels.Sub;
using System;
using System.IO;
using System.Threading.Tasks;
using Windows.Graphics.Imaging;

namespace SyncClipboard.WinUI3.Utilities;

internal static class DragUiHelper
{
    public static async Task SetDragIconAsync(DragUI dragUI, HistoryRecordVM record)
    {
        if (record.Type != ProfileType.Image
            || string.IsNullOrEmpty(record.PreviewImage)
            || !File.Exists(record.PreviewImage))
        {
            return;
        }

        var image = await CreateImagePreviewAsync(record.PreviewImage);
        if (image is not null)
        {
            dragUI.SetContentFromBitmapImage(image);
        }
    }

    private static async Task<BitmapImage?> CreateImagePreviewAsync(string imagePath)
    {
        try
        {
            using var stream = File.OpenRead(imagePath);
            var decoder = await BitmapDecoder.CreateAsync(stream.AsRandomAccessStream());
            var (width, height) = GetPreviewSize(decoder.PixelWidth, decoder.PixelHeight);
            return new BitmapImage(new Uri(imagePath))
            {
                DecodePixelWidth = width,
                DecodePixelHeight = height,
            };
        }
        catch (Exception)
        {
            return null;
        }
    }

    private static (int width, int height) GetPreviewSize(uint originalWidth, uint originalHeight)
    {
        const int maxSize = 128;
        if (originalWidth == 0 || originalHeight == 0)
        {
            return (maxSize, maxSize);
        }

        var scale = maxSize / (double)Math.Max(originalWidth, originalHeight);
        return (
            Math.Max(1, (int)Math.Round(originalWidth * scale)),
            Math.Max(1, (int)Math.Round(originalHeight * scale)));
    }
}
