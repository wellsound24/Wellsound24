#define MyAppName "Well ClearWarm EQ"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Well Audio"
#define StandaloneExe "..\build-eq\WellClearWarmEQ_artefacts\Release\Standalone\Well ClearWarm EQ.exe"
#define VST3Dir "..\build-eq\WellClearWarmEQ_artefacts\Release\VST3\Well ClearWarm EQ.vst3"

[Setup]
AppId={{6B13877B-593F-4B75-A9B4-0B6E9855E2C1}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Well Audio\Well ClearWarm EQ
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=Well_ClearWarm_EQ_v1_0_Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
UninstallDisplayName={#MyAppName}

[Files]
Source: "{#StandaloneExe}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#VST3Dir}\*"; DestDir: "{commoncf64}\VST3\Well ClearWarm EQ.vst3"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autodesktop}\Well ClearWarm EQ"; Filename: "{app}\Well ClearWarm EQ.exe"
Name: "{group}\Well ClearWarm EQ"; Filename: "{app}\Well ClearWarm EQ.exe"

[Run]
Filename: "{app}\Well ClearWarm EQ.exe"; Description: "เปิด Well ClearWarm EQ"; Flags: nowait postinstall skipifsilent
