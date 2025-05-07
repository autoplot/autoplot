This has the PDS header and data in one file.  Also, a runtime error was attempting to read the file:
* https://archives.esac.esa.int/psa/ftp/VENUS-EXPRESS/MAG/VEX-V-Y-MAG-4-V1.0/DATA/CAPTORBIT_S004/MAG_20060424_DOY114_S004_V1.TAB

# PDS3
This shows a couple of problems with the location of pointer file and bytes per field in items>1.
* https://pds-ppi.igpp.ucla.edu/data/CO-V_E_J_S_SS-RPWS-2-REFDR-WBRFULL-V1.0/DATA/RPWS_WIDEBAND_FULL/T19990XX/T1999003/T1999003_01_10KHZ2_WBRFR.LBL

Item bytes was miscalculated in code, but I think correcting the calculation might break things elsewhere.
* https://pds-ppi.igpp.ucla.edu/data/VG1-J_S_SS-PWS-1-EDR-WFRM-60MS-V1.0/DATA/WFRM/P9/V1P9_002/C0105058.LBL?WAVEFORM_BYTE

Shows the LABEL in the LABEL directory:
* https://pds-ppi.igpp.ucla.edu/data/CO-V_E_J_S_SS-RPWS-2-REFDR-WFRFULL-V1.0/DATA/RPWS_WAVEFORM_FULL/T20000XX/T2000037/T2000037_25HZ4_WFRFR.LBL?WFR_SAMPLE

Bunch from test144:
* https://pds-ppi.igpp.ucla.edu/data/CO-E_SW_J_S-MAG-4-SUMM-1SECAVG-V2.0/DATA/2000/00183_00274_FGM_RTN_1S.LBL?BTOTAL&X=TIME
* https://pds-ppi.igpp.ucla.edu/data/JNO-J-JED-3-CDR-V1.0/DATA/2016/366/JED_090_HIERSESP_CDR_2016366_V03.LBL?T0EXF14+FLUX&X=UTC
* https://pds-ppi.igpp.ucla.edu/data/GO-J-PLS-5-RTS-MOMENTS-V1.0/DATA/PLS_PDS_RTS_ORB03.LBL?density&X=TIME
* https://pds-ppi.igpp.ucla.edu/data/GO-J-PWS-2-EDR-WAVEFORM-80KHZ-V1.0/DATA/C032095/80KHZ_0320950402.LBL?WAVEFORM_SAMPLES
* https://pds-ppi.igpp.ucla.edu/data/GO-J-PWS-5-DDR-PLASMA-DENSITY-FULL-V1.0/DATA/00_JUPITER/FPE_1996_05_26_V01.LBL?FREQ_CE

Juno:
* https://pds-ppi.igpp.ucla.edu/data/JNO-J_SW-JAD-5-CALIBRATED-V1.0/DATA/2018/2018091/ELECTRONS/JAD_L50_HRS_ELC_TWO_DEF_2018091_V01.LBL?DATA

Pathological cases:
* https://github.com/autoplot/dev/blob/master/demos/2024/20241214/VG2-U-PRA-3-RDR-LOWBAND-6SEC.jyds
* https://pds-ppi.igpp.ucla.edu/data/VG1-J-PRA-3-RDR-LOWBAND-6SEC-V1.0/DATA/PRA_I.LBL?SWEEP1

# PDS4
* https://pds-ppi.igpp.ucla.edu/data/cassini-rpws-electron_density/data/2017/rpws_fpe_2017-102_v1.xml uses Java 11 csv parser.
* https://pds-ppi.igpp.ucla.edu/data/cassini-caps-calibrated/data-els/2012/092_121_APR/ELS_201209206_V01.xml?DATA&X=SC_POS_R  Doesn't pick up timetags.

Does not work with the last production release:
* https://pds-ppi.igpp.ucla.edu/data/juno-waves-electron-density/data_io/2024017_orbit_58/wav_2024-034T00-00-00_e-dens-i_v1.0.lblx?Fpe&X=SCET

Here is the search engine:
* https://pds.nasa.gov/tools/doi/#/search

VG2-J-PWS-5-DDR-PLASMA-DENSITY-1S-V1.0
GO-J-PWS-5-DDR-PLASMA-DENSITY-FULL-V1.0

