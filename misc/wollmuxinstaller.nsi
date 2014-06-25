# WollMux Installer
# -----------------
# This is the Nullsoft Scriptable Install System (NSIS) script for generating
# the WollMux Installer for Windows.
# 
# Copyright (c) 2011 Landeshauptstadt M�nchen
# Author: Daniel Benkmann
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the European Union Public Licence (EUPL), 
# version 1.0 (or any later version).
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# European Union Public Licence for more details.
# 
# You should have received a copy of the European Union Public Licence
# along with this program. If not, see 
# http://www.osor.eu/eupl

!include FileFunc.nsh
!insertmacro GetParameters
!insertmacro GetOptions
!include Sections.nsh
!include wollmuxinstaller_lang.nsh ;; Include our own language specific settings and strings

!ifndef VERSION ;; WollMux version
	!warning "VERSION was not defined, so used 'x.x.x' as default! Call makensis with the -D switch to set the version number."
	!define VERSION "x.x.x" ;; default
!endif

!ifndef FILESDIR ;; where to find the installation files
	!warning "FILESDIR was not defined, so used 'files' as default! \
	          Call makensis with the -D switch to set the directory containing the installation files."
	!define FILESDIR "files" ;; default
!endif

!ifndef WOLLMUX ;; name to use for WollMux application
	!define WOLLMUX "WollMux" ;; default
!endif

!ifndef WOLLMUXBAR ;; name to use for the WollMuxBar
	!define WOLLMUXBAR "WollMuxBar" ;; default
!endif

!ifndef WOLLMUXBAR_EXE_NAME ;; name of the wollmuxbar.exe
	!define WOLLMUXBAR_EXE_NAME "wollmuxbar.exe" ;; default
!endif

!ifndef WOLLMUXBAR_JAR_NAME ;; name of the WollMuxBar.jar file
	!define WOLLMUXBAR_JAR_NAME "WollMuxBar.jar" ;; default
!endif


Name "${WOLLMUX} ${VERSION}"
OutFile "${WOLLMUX}-${VERSION}-installer.exe"
Caption "${WOLLMUX} Installer"
BrandingText "(c) Landeshauptstadt M�nchen" ;; string to replace "Nullsoft Install System vX.XX" at the bottom of install window

RequestExecutionLevel admin ;; needed to set ExecutionLevel for Vista/Windows 7 - works only with NSIS ver. 2.21+
AllowRootDirInstall true ;; not necessary but why restrict the user?
AllowSkipFiles off
ShowInstDetails hide
ShowUninstDetails hide

# Set Default Installation Directory
InstallDir "$PROGRAMFILES\${WOLLMUX}" ;; default ($INSTDIR will be overwritten in .onInit function if "--INSTDIR=" command line parameter is set)

# Installer & Uninstaller Pages
Page components ;; Soll der User �berhaupt was ausw�hlen d�rfen?
Page directory
Page instfiles
UninstPage uninstConfirm
UninstPage instFiles


# VARIABLE DECLARATIONS:
# 
# The sharedSwitch variable is used by the unopkg call in the installer section
# It is declared here because it is not only used in the installer section but also in .onInit
# since the optional "--LOCAL" command line parameter can overwrite it
Var sharedSwitch
# 
# The cmdParameters variable is used to store the command line parameters given in the call
# of the installer or uninstaller. It is declared here globally because it is not only used
# in .onInit but also in un.onInit and GetOOoPath
Var cmdParameters



################################ Installer Sections ################################
SectionGroup /e "${WOLLMUX}"
Section "${WOLLMUXBAR} & OOo Extension"
	SectionIn 1 RO ;; section is "read-only", i.e. it can't be unselected
	
	# create temporary directory
	GetTempFileName $R9
	Delete $R9 ;; delete tempfile because we want a directory
	SetOutPath $R9 ;; creates directory with TempFileName and sets the output path ($OUTDIR) to it
	
	# extract the files
	File ${FILESDIR}\${WOLLMUX}.oxt
	File ${FILESDIR}\${WOLLMUXBAR_JAR_NAME}
	File ${FILESDIR}\${WOLLMUXBAR_EXE_NAME}
	
	# since the installation of the OOo extension is the most critical part of the installation we try that first
	Call GetOOoPath
	Pop $R0
	StrCmp $R0 "NOTFOUND" 0 +3
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(NoOOoFoundMessage) /SD IDOK
	  Goto skipunopkg
	

	
  unopkg:
	;; First we try to remove previously installed WollMux extensions (local as well as shared). If errors occur when removing the extension it's likely because
	;; the WollMux wasn't installed, so we do nothing in that case; other errors will reoccur below when adding the new extension
	DetailPrint $(UnoPkgRemoveMessage)
	ClearErrors
	ExecWait '"$R0\unopkg" remove WollMux.uno.pkg'
	ExecWait '"$R0\unopkg" remove WollMux.uno.pkg --shared'
	ExecWait '"$R0\unopkg" remove de.muenchen.allg.d101.wollmux'
	ExecWait '"$R0\unopkg" remove de.muenchen.allg.d101.wollmux --shared'
	IfErrors 0 ;; we do nothing on errors here, see above
	
	;; Now we try to install the new WollMux.oxt
	DetailPrint $(UnoPkgAddMessage)
	ClearErrors
	ExecWait '"$R0\unopkg" add "$R9\${WOLLMUX}.oxt" $sharedSwitch'
	IfErrors 0 +6
	  MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION $(UnoPkgErrorMessage) /SD IDCANCEL IDRETRY unopkg IDCANCEL 0
	  SetOutPath $INSTDIR ;; current working directory can't be deleted so we change it
	  RMDir /r $R9 ;; delete temporary files
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(AbortUnoPkgErrorMessage) /SD IDOK
	  Abort $(AbortMessage)
	
  skipunopkg:
	# create $INSTDIR (if it doesn't already exist)
	CreateDirectory $INSTDIR
	
	# create uninstaller
	WriteUninstaller "$INSTDIR\wollmux_uninstall.exe"
	
	#copy files from temporary folder to $INSTDIR
	SetOutPath $INSTDIR ;; necessary or else we can't delete the temporary folder later on
	ClearErrors
	CopyFiles /SILENT $R9\*.* $INSTDIR
	IfErrors 0 +4
	  RMDir /r $R9 ;; delete temporary files
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(AbortFileCopy) /SD IDOK
	  Abort $(AbortMessage)
	
	# remove temporary folder
	RMDir /r $R9
	
	# write registry entries for "Add/Remove Programs"
	WriteRegStr SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "DisplayName" "${WOLLMUX}"
	WriteRegStr SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "UninstallString" "$\"$INSTDIR\wollmux_uninstall.exe$\""
	WriteRegStr SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "QuietUninstallString" "$\"$INSTDIR\wollmux_uninstall.exe$\" /S"
	WriteRegStr SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "InstallLocation" "$\"$INSTDIR$\""
	WriteRegStr SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "Publisher" "http://www.wollmux.org"
	WriteRegStr SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "URLInfoAbout" "http://www.wollmux.org"
	WriteRegStr SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "DisplayVersion" "${VERSION}"
	WriteRegDWORD SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "NoModify" 0x00000001
	WriteRegDWORD SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "NoRepair" 0x00000001
	
	# write "JavaHome" registry key
	ReadRegStr $R0 SHELL_CONTEXT "Software\WollMux" "JavaHome"
    StrCmp $R0 "" 0 +2  ;; check if JavaHome entry is empty (or doesn't exist); if it isn't empty we don't overwrite it
	WriteRegStr SHELL_CONTEXT "Software\WollMux" "JavaHome" ""
SectionEnd

Section $(StartMenuShortcut) startmenu_section_id
	ClearErrors
	CreateDirectory "$SMPROGRAMS\${WOLLMUX}"
	CreateShortCut "$SMPROGRAMS\${WOLLMUX}\${WOLLMUXBAR}.lnk" "$INSTDIR\${WOLLMUXBAR_EXE_NAME}"
	CreateShortCut "$SMPROGRAMS\${WOLLMUX}\$(UninstallWollMux).lnk" "$INSTDIR\wollmux_uninstall.exe"
	IfErrors 0 +2
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(StartMenuShortcutErrorMessage) /SD IDOK
SectionEnd

Section $(DesktopShortcut) desktop_section_id
	ClearErrors
	CreateShortCut "$DESKTOP\${WOLLMUXBAR}.lnk" "$INSTDIR\${WOLLMUXBAR_EXE_NAME}"
	IfErrors 0 +2
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(DesktopShortcutErrorMessage) /SD IDOK
SectionEnd
SectionGroupEnd



################################ Uninstaller Sections ################################
SectionGroup "un.${WOLLMUX}"
Section "un.${WOLLMUXBAR} & OOo Extension"
	Delete $INSTDIR\wollmux_uninstall.exe
	Delete $INSTDIR\${WOLLMUX}.oxt
	Delete $INSTDIR\${WOLLMUXBAR_JAR_NAME}
	Delete $INSTDIR\${WOLLMUXBAR_EXE_NAME}
	RMDir $INSTDIR ;; deletes the directory only if it is empty
	Delete $PROFILE\.wollmux\wollmux.log
	;;Delete $PROFILE\.wollmux\cache.conf
	RMDir $PROFILE\.wollmux ;; deletes the directory only if it is empty
		
	# Delete registry keys created for "Add/Remove Programs" (only if they really point to this uninstaller!)
	ReadRegStr $R0 SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}" "UninstallString"
	StrCmp $R0 "$\"$INSTDIR\wollmux_uninstall.exe$\"" 0 +2
	DeleteRegKey SHELL_CONTEXT "Software\Microsoft\Windows\CurrentVersion\Uninstall\${WOLLMUX}"
	
	# try to uninstall WollMux.oxt extension with unopkg
	Call un.GetOOoPath
	Pop $R0
	StrCmp $R0 "NOTFOUND" 0 +3
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(unNoOOoFoundMessage) /SD IDOK
	  Goto skipunopkg
	
	ExecWait '"$R0\unopkg" remove de.muenchen.allg.d101.wollmux --shared'
	
  skipunopkg:
	IfFileExists $INSTDIR\*.* 0 +2
	MessageBox MB_OK|MB_ICONINFORMATION $(InstDirNotDeletedMessage) /SD IDOK
SectionEnd

Section "un.$(StartMenuShortcut)"
	Delete "$SMPROGRAMS\${WOLLMUX}\${WOLLMUXBAR}.lnk"
	Delete "$SMPROGRAMS\${WOLLMUX}\$(UninstallWollMux).lnk"
	RMDir "$SMPROGRAMS\${WOLLMUX}"
SectionEnd

Section "un.$(DesktopShortcut)"
	Delete "$DESKTOP\${WOLLMUXBAR}.lnk"
SectionEnd
SectionGroupEnd



################################ Functions ################################
Function .onInit
	Push $R0
	Push $R1

	# Initialize sharedSwitch variable
	StrCpy $sharedSwitch "--shared" ;; "--shared" is default
	
	# Get command line parameters
	${GetParameters} $cmdParameters
	
	# Set SilentInstall if command line parameter "--SILENT" was used
	ClearErrors
	${GetOptions} $cmdParameters "--SILENT" $R1 ;; read optional "--SILENT" parameter
	IfErrors +2
	SetSilent silent
	
	# Check if command line parameter "--LOCAL" was used - if so try local installation instead of shared install
	# THIS SWITCH IS NOT SUPPORTED BY THE UNINSTALLER!
	ClearErrors
	${GetOptions} $cmdParameters "--LOCAL" $R1 ;; read optional "--LOCAL" parameter
	IfErrors +4
	  SetShellVarContext current
	  StrCpy $sharedSwitch ""
	  Goto skipadmincheck
	
	# check if user is admin or power user - if not abort
	UserInfo::GetAccountType
	Pop $R0
	StrCmp $R0 "Admin" +4
	StrCmp $R0 "Power" +3
	MessageBox MB_OK|MB_ICONEXCLAMATION $(NeedAdminMessage) /SD IDOK
	Abort

	# set context to "all users" for installation
	SetShellVarContext all ;; default is "current"
	
  skipadmincheck:
	
	# Check if command line parameter "--NOKILL" was used - if so we skip execution of TerminateOOo.jar
	ClearErrors
	${GetOptions} $cmdParameters "--NOKILL" $R1 ;; read optional "--NOKILL" parameter
	IfErrors 0 skipKillOOo
	
	# Inform User that we will try to close OpenOffice.org
	MessageBox MB_OKCANCEL|MB_ICONINFORMATION $(TryToKillOOoMessage) /SD IDOK IDOK +2
	Abort
	# Try to kill OOo using TerminateOOo.jar. If unsuccessful abort installation with message.
	File /oname=$TEMP\TerminateOOo.jar ${FILESDIR}\TerminateOOo.jar ;; extract TerminateOOo.jar to temporary directory
	Call GetJRE
	Pop $R0
	StrCmp $R0 "NOTFOUND" 0 +4 ;; check if Java was found
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(NoJavaFoundMessage) /SD IDOK
	  Delete $TEMP\TerminateOOo.jar
	  Abort
	ClearErrors
	ExecWait '"$R0" -jar "$TEMP\TerminateOOo.jar"' ;; does not work if WollMuxBar is running with "--quickstarter" option
	IfErrors 0 +4
	  MessageBox MB_OK|MB_ICONEXCLAMATION $(OOoRunningMessage) /SD IDOK ;; we would also get this error if no Java was found, but we check for that above
	  Delete $TEMP\TerminateOOo.jar
	  Abort
	
	Delete $TEMP\TerminateOOo.jar
	
  skipKillOOo:
	
	# Set $INSTDIR if command line parameter "--INSTDIR=" was used (this is done in .onInit so the directory page contains the right value when it is displayed)
	ClearErrors
	${GetOptions} $cmdParameters "--INSTDIR=" $R1 ;; read optional "--INSTDIR=" parameter
	IfErrors +2 0
	StrCpy $INSTDIR $R1 ;; set installation directory

	# Unselect "Start Menu Shortcut" section if command line parameter "--NOSTARTMENU" was used
	ClearErrors
	${GetOptions} $cmdParameters "--NOSTARTMENU" $R1 ;; read optional "--NOSTARTMENU" parameter
	IfErrors nostartmenudone
	!insertmacro UnselectSection ${startmenu_section_id}
  nostartmenudone:
	
	# Unselect "Desktop Shortcut" section if command line parameter "--NODESKTOP" was used
	ClearErrors
	${GetOptions} $cmdParameters "--NODESKTOP" $R1 ;; read optional "--NODESKTOP" parameter
	IfErrors nodesktopdone
	!insertmacro UnselectSection ${desktop_section_id}
  nodesktopdone:
	
	Pop $R1
	Pop $R0
FunctionEnd

Function un.onInit
	Push $R0
	Push $R1
	
	# Get command line parameters
	${GetParameters} $cmdParameters
	
	# Check if user is admin or power user - if not abort
	UserInfo::GetAccountType
	Pop $R0
	StrCmp $R0 "Admin" +4
	StrCmp $R0 "Power" +3
	MessageBox MB_OK|MB_ICONEXCLAMATION $(NeedAdminMessage) /SD IDOK
	Abort
	
	# Set context to "all users" for uninstallation
	SetShellVarContext all ;; default is "current"
	
	# Check if command line parameter "--NOKILL" was used - if so we skip execution of TerminateOOo.jar
	ClearErrors
	${GetOptions} $cmdParameters "--NOKILL" $R1 ;; read optional "--NOKILL" parameter
	IfErrors 0 skipKillOOo
	
	# Inform User that we will try to close OpenOffice.org
	MessageBox MB_OKCANCEL|MB_ICONINFORMATION $(TryToKillOOoMessage) /SD IDOK IDOK +2
	Abort
	# Try to kill OOo using TerminateOOo.jar. If that doesn't work we give the user the option to try to uninstall anyway.
	File /oname=$TEMP\TerminateOOo.jar ${FILESDIR}\TerminateOOo.jar ;; extract TerminateOOo.jar to temporary directory
	Call un.GetJRE
	Pop $R0
	StrCmp $R0 "NOTFOUND" 0 +4 ;; check if Java was found
	  MessageBox MB_YESNO|MB_ICONEXCLAMATION $(OOoKillFailedMessage) /SD IDYES IDYES skipKillOOo
	  Delete $TEMP\TerminateOOo.jar
	  Abort
	ClearErrors
	ExecWait '"$R0" -jar "$TEMP\TerminateOOo.jar"' ;; does not work if WollMuxBar is running with "--quickstarter" option
	IfErrors 0 +4
	  MessageBox MB_YESNO|MB_ICONEXCLAMATION $(OOoKillFailedMessage) /SD IDYES IDYES skipKillOOo ;; we would also get this error if no Java was found, but we check for that above
	  Delete $TEMP\TerminateOOo.jar
	  Abort

  skipKillOOo:	  
	Delete $TEMP\TerminateOOo.jar
	
	Pop $R1
	Pop $R0
FunctionEnd


!macro GetOOOPath UN
Function ${UN}GetOOoPath
	# This function returns the full path of the current OpenOffice.org (or LibreOffice) installation.
	# It looks in:
	#  1 - the registry value "" (default) of the key "HKCU\Software\OpenOffice.org\UNO\InstallPath"
	#  2 - the registry value "" (default) of the key "HKLM\Software\OpenOffice.org\UNO\InstallPath"
	#  3 - the registry value "" (default) of the key "HKCU\Software\OpenOffice\UNO\InstallPath"
	#  4 - the registry value "" (default) of the key "HKLM\Software\OpenOffice\UNO\InstallPath"
	#  5 - the registry value "" (default) of the key "HKCU\Software\LibreOffice\UNO\InstallPath"
	#  6 - the registry value "" (default) of the key "HKLM\Software\LibreOffice\UNO\InstallPath"
	#
	# If the path could not be found the string "NOTFOUND" is returned.
	# If the command line parameter "--LIBRE" is set we skip the search in 1) and 2).
	

	Push $R0
	Push $R1
	
	# Check if command line parameter "--LIBRE" was used - if so we skip search for OpenOffice.org registry keys
	ClearErrors
	${GetOptions} $cmdParameters "--LIBRE" $R1 ;; read optional "--LIBRE" parameter
	IfErrors 0 skipOOoReg
	
	ClearErrors
	ReadRegStr $R0 HKCU "Software\OpenOffice.org\UNO\InstallPath" ""
	IfErrors +3
	StrCmp $R0 "" +2  ;; check if entry is empty
	IfFileExists $R0\*.* OOoFound  ;; 1) found path in the HKCU OOo registry key
	StrCpy $R0 "NOTFOUND"

	ClearErrors
	ReadRegStr $R0 HKLM "Software\OpenOffice.org\UNO\InstallPath" ""
	IfErrors +3
	StrCmp $R0 "" +2  ;; check if entry is empty
	IfFileExists $R0\*.* OOoFound  ;; 2) found path in the HKLM OOo registry key
	StrCpy $R0 "NOTFOUND"

	ClearErrors
	ReadRegStr $R0 HKCU "Software\OpenOffice\UNO\InstallPath" ""
	IfErrors +3
	StrCmp $R0 "" +2  ;; check if entry is empty
	IfFileExists $R0\*.* OOoFound  ;; 3) found path in the HKCU AOO registry key
	StrCpy $R0 "NOTFOUND"

	ClearErrors
	ReadRegStr $R0 HKLM "Software\OpenOffice\UNO\InstallPath" ""
	IfErrors +3
	StrCmp $R0 "" +2  ;; check if entry is empty
	IfFileExists $R0\*.* OOoFound  ;; 4) found path in the HKLM AOO registry key
	StrCpy $R0 "NOTFOUND"

  skipOOoReg:
	ClearErrors
	ReadRegStr $R0 HKCU "Software\LibreOffice\UNO\InstallPath" ""
	IfErrors +3
	StrCmp $R0 "" +2  ;; check if entry is empty
	IfFileExists $R0\*.* OOoFound  ;; 5) found path in the HKCU LibreOffice registry key
	StrCpy $R0 "NOTFOUND"

	ClearErrors
	ReadRegStr $R0 HKLM "Software\LibreOffice\UNO\InstallPath" ""
	IfErrors +3
	StrCmp $R0 "" +2  ;; check if entry is empty
	IfFileExists $R0\*.* OOoFound  ;; 6) found path in the HKLM LibreOffice registry key
	StrCpy $R0 "NOTFOUND"
 
  OOoFound:
	Pop $R1
	Exch $R0
FunctionEnd
!macroend

!insertmacro GetOOOPath "" ;; GetOOOPath function for installer
!insertmacro GetOOOPath "un." ;; un.GetOOOPath function for uninstaller


; use javaw.exe to avoid dosbox.
; use java.exe to keep stdout/stderr
!define JAVAEXE "javaw.exe"
!macro GetJRE UN
Function ${UN}GetJRE
;
;  This function returns the full path of a valid java.exe (javaw.exe)
;  looks in:
;  1 - JAVA_HOME environment variable
;  2 - the registry value set by the Java Installation in HKLM
;
; If the path could not be found the string "NOTFOUND" is returned
 
  Push $R0
  Push $R1
  
  ClearErrors
  ReadEnvStr $R0 "JAVA_HOME"
  IfErrors +3
  StrCpy $R0 "$R0\bin\${JAVAEXE}"
  IfFileExists $R0 JreFound  ;; 1) found it in JAVA_HOME
  StrCpy $R0 ""
 
  ClearErrors
  ReadRegStr $R1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
  ReadRegStr $R0 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$R1" "JavaHome"
  IfErrors +3
  StrCpy $R0 "$R0\bin\${JAVAEXE}"
  IfFileExists $R0 JreFound  ;; 2) found it in the registry key set by Java
  
  StrCpy $R0 "NOTFOUND"
 
 JreFound:
  Pop $R1
  Exch $R0
FunctionEnd
!macroend

!insertmacro GetJRE ""
!insertmacro GetJRE "un."