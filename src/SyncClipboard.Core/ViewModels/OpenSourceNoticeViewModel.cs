using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using SyncClipboard.Core.Commons;
using SyncClipboard.Core.Models;
using SyncClipboard.Core.Utilities;
using System;
using System.Collections.ObjectModel;

namespace SyncClipboard.Core.ViewModels;

public partial class OpenSourceNoticeViewModel : ObservableObject
{
    private readonly ConfigManager _configManager;
    private readonly AboutViewModel _aboutViewModel;

    public OpenSourceNoticeViewModel(ConfigManager configManager, AboutViewModel aboutViewModel)
    {
        _configManager = configManager;
        _aboutViewModel = aboutViewModel;
        Dependencies = new ObservableCollection<OpenSourceSoftware>(_aboutViewModel.Dependencies);
        ProjectLicenseText = OssNoticeHelper.TryReadProjectLicenseText() ?? string.Empty;
    }

    public static string ForkRepositoryUrl => OssNoticeHelper.ForkRepositoryUrl;
    public static string UpstreamRepositoryUrl => OssNoticeHelper.UpstreamRepositoryUrl;
    public static string ProjectLicenseName => OssNoticeHelper.ProjectLicenseName;
    public static string ProjectCopyright => OssNoticeHelper.ProjectCopyright;
    public ObservableCollection<OpenSourceSoftware> Dependencies { get; }
    public string ProjectLicenseText { get; }

    [ObservableProperty]
    private bool isFirstRun = true;

    [RelayCommand]
    private static void OpenForkRepository() => Sys.OpenWithDefaultApp(ForkRepositoryUrl);

    [RelayCommand]
    private static void OpenUpstreamRepository() => Sys.OpenWithDefaultApp(UpstreamRepositoryUrl);

    [RelayCommand]
    private static void OpenUrl(string? url)
    {
        if (!string.IsNullOrWhiteSpace(url))
        {
            Sys.OpenWithDefaultApp(url);
        }
    }

    [RelayCommand]
    private void Acknowledge()
    {
        OssNoticeHelper.Acknowledge(_configManager);
        Acknowledged?.Invoke();
    }

    public event Action? Acknowledged;
}
