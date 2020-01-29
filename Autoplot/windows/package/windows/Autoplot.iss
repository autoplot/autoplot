;This file will be executed next to the application bundle image
;I.e. current directory will contain folder Autoplot with application files
[Setup]
AppId={{org.autoplot}}
AppName=Autoplot
AppVersion=2020a1
AppVerName=Autoplot 2020a1
AppPublisher=Das Developers
AppComments=Autoplot
AppCopyright=Copyright (C) 2020
;AppPublisherURL=http://java.com/
;AppSupportURL=http://java.com/
;AppUpdatesURL=http://java.com/
DefaultDirName={localappdata}\Autoplot
DisableStartupPrompt=Yes
DisableDirPage=No
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=Yes
DefaultGroupName=Das Developers
;Optional License
LicenseFile=
;WinXP or above
MinVersion=0,5.1 
OutputBaseFilename=Autoplot-2020a1
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
SetupIconFile=Autoplot\Autoplot.ico
UninstallDisplayIcon={app}\Autoplot.ico
UninstallDisplayName=Autoplot
WizardImageStretch=Yes
WizardSmallImageFile=Autoplot-setup-icon.bmp   
ArchitecturesInstallIn64BitMode=x64
ChangesAssociations=yes

; File associations are only setup for the current user to avoid needing Admin rights. -cwp
[Registry]
Root: HKCU; Subkey: "Software\Classes\.vap"; ValueType: string; ValueName: ""; ValueData: "AutoplotState"; Flags: uninsdeletevalue
Root: HKCU; Subkey: "Software\Classes\AutoplotState"; ValueType: string; ValueName: ""; ValueData: "Autoplot State"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\AutoplotState\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\Autoplot.exe,0"
Root: HKCU; Subkey: "Software\Classes\AutoplotState\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\Autoplot.exe"" ""%1"""
Root: HKCU; Subkey: "Software\Classes\.pngwalk"; ValueType: string; ValueName: ""; ValueData: "PNGwalk"; Flags: uninsdeletevalue
Root: HKCU; Subkey: "Software\Classes\PNGwalk"; ValueType: string; ValueName: ""; ValueData: "PNG Walk"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\PNGwalk\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\Autoplot.exe,0"
Root: HKCU; Subkey: "Software\Classes\PNGwalk\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\Autoplot.exe"" ""%1"""

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "Autoplot\Autoplot.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "Autoplot\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Autoplot"; Filename: "{app}\Autoplot.exe"; IconFilename: "{app}\Autoplot.ico"; Check: returnTrue()
Name: "{commondesktop}\Autoplot"; Filename: "{app}\Autoplot.exe";  IconFilename: "{app}\Autoplot.ico"; Check: returnFalse()


[Run]
Filename: "{app}\Autoplot.exe"; Parameters: "-Xappcds:generatecache"; Check: returnFalse()
Filename: "{app}\Autoplot.exe"; Description: "{cm:LaunchProgram,Autoplot}"; Flags: nowait postinstall skipifsilent; Check: returnTrue()
Filename: "{app}\Autoplot.exe"; Parameters: "-install -svcName ""Autoplot"" -svcDesc ""A browser for data on the web."" -mainExe ""Autoplot.exe""  "; Check: returnFalse()

[UninstallRun]
Filename: "{app}\Autoplot.exe "; Parameters: "-uninstall -svcName Autoplot -stopOnUninstall"; Check: returnFalse()

[Code]
function returnTrue(): Boolean;
begin
  Result := True;
end;

function returnFalse(): Boolean;
begin
  Result := False;
end;

function InitializeSetup(): Boolean;
begin
// Possible future improvements:
//   if version less or same => just launch app
//   if upgrade => check if same app is running and wait for it to exit
//   Add pack200/unpack200 support? 
  Result := True;
end;  
