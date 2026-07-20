using AppKit;
using Foundation;
using SyncClipboard.Core.Interfaces;
using System;

namespace SyncClipboard.Desktop.MacOS.Utilities;

internal sealed class MacForegroundWindowWatcher(IThreadDispatcher threadDispatcher) : IForegroundWindowWatcher
{
    private readonly IThreadDispatcher _threadDispatcher = threadDispatcher;
    private NSObject? _observer;

    public event Action? ForegroundWindowChanged;

    public void Start()
    {
        if (_observer != null)
        {
            return;
        }

        _ = _threadDispatcher.RunOnMainThreadAsync(() =>
        {
            _observer = NSWorkspace.Notifications.ObserveDidActivateApplication((_, __) =>
            {
                ForegroundWindowChanged?.Invoke();
            });
        });
    }

    public void Stop()
    {
        if (_observer is null)
        {
            return;
        }

        _ = _threadDispatcher.RunOnMainThreadAsync(() =>
        {
            _observer?.Dispose();
            _observer = null;
        });
    }

    public void Dispose() => Stop();
}
