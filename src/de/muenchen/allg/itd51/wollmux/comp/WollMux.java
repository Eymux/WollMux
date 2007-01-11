/*
 * Dateiname: WollMux.java
 * Projekt  : WollMux
 * Funktion : zentraler UNO-Service WollMux 
 * 
 * Copyright: Landeshauptstadt M�nchen
 *
 * �nderungshistorie:
 * Datum      | Wer | �nderungsgrund
 * -------------------------------------------------------------------
 * 14.10.2005 | LUT | Erstellung
 * 09.11.2005 | LUT | + Logfile wird jetzt erweitert (append-modus)
 *                    + verwenden des Konfigurationsparameters SENDER_SOURCE
 *                    + Erster Start des wollmux �ber wm_configured feststellen.
 * 05.12.2005 | BNK | line.separator statt \n                 |  
 * 06.06.2006 | LUT | + Abl�sung der Event-Klasse durch saubere Objektstruktur
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */

package de.muenchen.allg.itd51.wollmux.comp;

import com.sun.star.beans.NamedValue;
import com.sun.star.frame.DispatchDescriptor;
import com.sun.star.frame.XDispatch;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.lib.uno.helper.Factory;
import com.sun.star.lib.uno.helper.WeakBase;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.task.XAsyncJob;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.XComponentContext;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.wollmux.DispatchHandler;
import de.muenchen.allg.itd51.wollmux.Logger;
import de.muenchen.allg.itd51.wollmux.WollMuxEventHandler;
import de.muenchen.allg.itd51.wollmux.WollMuxSingleton;
import de.muenchen.allg.itd51.wollmux.XPALChangeEventListener;
import de.muenchen.allg.itd51.wollmux.XWollMux;

/**
 * Diese Klasse stellt den zentralen UNO-Service WollMux dar. Der Service hat
 * drei Funktionen: als XAsyncJob sorgt der Service daf�r, dass das
 * WollMuxSingleton beim Starten von OpenOffice initialisiert wird (er startet
 * also den WollMux). Als XDispatchProvider und XDispatch behandelt er alle
 * "wollmux:kommando..." URLs und als XWollMux stellt er die Schnittstelle f�r
 * externe UNO-Komponenten dar.
 */
public class WollMux extends WeakBase implements XServiceInfo, XAsyncJob,
    XDispatchProvider, XWollMux
{

  /**
   * Dieses Feld ent�lt eine Liste aller Services, die dieser UNO-Service
   * implementiert.
   */
  public static final java.lang.String[] SERVICENAMES = {
                                                         "com.sun.star.task.AsyncJob",
                                                         "de.muenchen.allg.itd51.wollmux.WollMux" };

  /**
   * Der Konstruktor initialisiert das WollMuxSingleton und startet damit den
   * eigentlichen WollMux. Der Konstuktor wird aufgerufen, bevor OpenOffice.org
   * die Methode executeAsync() aufrufen kann, die bei einem
   * ON_FIRST_VISIBLE_TASK-Event �ber den Job-Mechanismus ausgef�hrt wird.
   * 
   * @param context
   */
  public WollMux(XComponentContext ctx)
  {
    WollMuxSingleton.initialize(ctx);
  }

  /**
   * Der AsyncJob wird mit dem Event OnFirstVisibleTask gestartet. Die Methode
   * selbst beendet sich sofort wieder, bevor die Methode jedoch ausgef�hrt
   * wird, wird im Konstruktor das WollMuxSingleton initialisiert.
   * 
   * @see com.sun.star.task.XAsyncJob#executeAsync(com.sun.star.beans.NamedValue[],
   *      com.sun.star.task.XJobListener)
   */
  public synchronized void executeAsync(com.sun.star.beans.NamedValue[] lArgs,
      com.sun.star.task.XJobListener xListener)
  {
    xListener.jobFinished(this, new NamedValue[] {});
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.sun.star.lang.XServiceInfo#getSupportedServiceNames()
   */
  public String[] getSupportedServiceNames()
  {
    return SERVICENAMES;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.sun.star.lang.XServiceInfo#supportsService(java.lang.String)
   */
  public boolean supportsService(String sService)
  {
    int len = SERVICENAMES.length;
    for (int i = 0; i < len; i++)
    {
      if (sService.equals(SERVICENAMES[i])) return true;
    }
    return false;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.sun.star.lang.XServiceInfo#getImplementationName()
   */
  public String getImplementationName()
  {
    return (WollMux.class.getName());
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.sun.star.frame.XDispatchProvider#queryDispatch(com.sun.star.util.URL,
   *      java.lang.String, int)
   */
  public XDispatch queryDispatch( /* IN */com.sun.star.util.URL aURL,
  /* IN */String sTargetFrameName,
  /* IN */int iSearchFlags)
  {
    return DispatchHandler.globalWollMuxDispatches.queryDispatch(
        aURL,
        sTargetFrameName,
        iSearchFlags);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.sun.star.frame.XDispatchProvider#queryDispatches(com.sun.star.frame.DispatchDescriptor[])
   */
  public XDispatch[] queryDispatches( /* IN */DispatchDescriptor[] seqDescripts)
  {
    return DispatchHandler.globalWollMuxDispatches
        .queryDispatches(seqDescripts);
  }

  /**
   * Diese Methode liefert eine Factory zur�ck, die in der Lage ist den
   * UNO-Service zu erzeugen. Die Methode wird von UNO intern ben�tigt. Die
   * Methoden __getComponentFactory und __writeRegistryServiceInfo stellen das
   * Herzst�ck des UNO-Service dar.
   * 
   * @param sImplName
   * @return
   */
  public synchronized static XSingleComponentFactory __getComponentFactory(
      java.lang.String sImplName)
  {
    com.sun.star.lang.XSingleComponentFactory xFactory = null;
    if (sImplName.equals(WollMux.class.getName()))
      xFactory = Factory.createComponentFactory(WollMux.class, SERVICENAMES);
    return xFactory;
  }

  /**
   * Diese Methode registriert den UNO-Service. Sie wird z.B. beim unopkg-add im
   * Hintergrund aufgerufen. Die Methoden __getComponentFactory und
   * __writeRegistryServiceInfo stellen das Herzst�ck des UNO-Service dar.
   * 
   * @param xRegKey
   * @return
   */
  public synchronized static boolean __writeRegistryServiceInfo(
      XRegistryKey xRegKey)
  {
    return Factory.writeRegistryServiceInfo(
        WollMux.class.getName(),
        WollMux.SERVICENAMES,
        xRegKey);
  }

  /**
   * Diese Methode registriert einen XPALChangeEventListener, der updates
   * empf�ngt wenn sich die PAL �ndert. Nach dem Registrieren wird sofort ein
   * ON_SELECTION_CHANGED Ereignis ausgel�st, welches daf�r sort, dass sofort
   * ein erster update aller Listener ausgef�hrt wird. Die Methode ignoriert
   * alle XPALChangeEventListenener-Instanzen, die bereits registriert wurden.
   * Mehrfachregistrierung der selben Instanz ist also nicht m�glich.
   * 
   * @see de.muenchen.allg.itd51.wollmux.XPALChangeEventBroadcaster#addPALChangeEventListener(de.muenchen.allg.itd51.wollmux.XPALChangeEventListener)
   */
  public void addPALChangeEventListener(XPALChangeEventListener l)
  {
    WollMuxEventHandler.handleAddPALChangeEventListener(l);
  }

  /**
   * Diese Methode deregistriert einen XPALChangeEventListener wenn er bereits
   * registriert war.
   * 
   * @see de.muenchen.allg.itd51.wollmux.XPALChangeEventBroadcaster#removePALChangeEventListener(de.muenchen.allg.itd51.wollmux.XPALChangeEventListener)
   */
  public void removePALChangeEventListener(XPALChangeEventListener l)
  {
    WollMuxEventHandler.handleRemovePALChangeEventListener(l);
  }

  /**
   * Diese Methode setzt den aktuellen Absender der Pers�nlichen Absenderliste
   * (PAL) auf den Absender sender. Der Absender wird nur gesetzt, wenn die
   * Parameter sender und idx in der alphabetisch sortierten Absenderliste des
   * WollMux �bereinstimmen - d.h. die Absenderliste der veranlassenden
   * SenderBox zum Zeitpunkt der Auswahl konsistent zur PAL des WollMux war. Die
   * Methode verwendet f�r sender das selben Format wie es vom
   * XPALProvider:getCurrentSender() geliefert wird.
   */
  public void setCurrentSender(String sender, short idx)
  {
    Logger.debug2("WollMux.setCurrentSender(\"" + sender + "\", " + idx + ")");
    WollMuxEventHandler.handleSetSender(sender, idx);
  }

  /**
   * Diese Methode setzt die zugeh�rige Druckfunktion des aktuellen
   * Vordergrunddokuments auf functionName, der ein g�ltiger Funktionsbezeichner
   * sein muss oder l�scht eine bereits gesetzte Druckfunktion, wenn
   * functionName der Leerstring ist.
   * 
   * @param functionName
   *          der Name der Druckfunktion (zum setzen) oder der Leerstring (zum
   *          l�schen). Der zu setzende Name muss ein g�ltiger
   *          Funktionsbezeichner sein und in einem Abschnitt "Druckfunktionen"
   *          in der wollmux.conf definiert sein.
   */
  public void setPrintFunction(String functionName)
  {
    XTextDocument doc = UNO.XTextDocument(UNO.desktop.getCurrentComponent());
    if (doc != null)
      WollMuxEventHandler.handleSetPrintFunction(doc, functionName);
  }
}
