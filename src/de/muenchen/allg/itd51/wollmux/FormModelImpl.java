/*
 * Dateiname: FormModelImpl.java
 * Projekt  : WollMux
 * Funktion : Implementierungen von FormModel (Zugriff auf die Formularbestandteile eines Dokuments)
 * 
 * Copyright: Landeshauptstadt M�nchen
 *
 * �nderungshistorie:
 * Datum      | Wer | �nderungsgrund
 * -------------------------------------------------------------------
 * 09.02.2007 | LUT | �bernahme aus DocumentCommandInterpreter
 *                    + MultiDocumentFormModel
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.parser.ConfigThingy;
import de.muenchen.allg.itd51.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.wollmux.dialog.DialogLibrary;
import de.muenchen.allg.itd51.wollmux.dialog.FormController;
import de.muenchen.allg.itd51.wollmux.dialog.FormGUI;
import de.muenchen.allg.itd51.wollmux.func.FunctionLibrary;

/**
 * Enth�lt alle Implementierungen von FormModel f�r den Zugriff auf die
 * Formularbestandteile eines Dokuments.
 */
public class FormModelImpl
{

  /**
   * Erzeugt ein FormModel f�r ein einfaches Formular mit genau einem
   * zugeh�rigen Formulardokument.
   * 
   * @param doc
   *          Das Dokument zu dem ein FormModel erzeugt werden soll.
   * @return ein FormModel dem genau ein Formulardokument zugeordnet ist.
   * @throws InvalidFormDescriptorException
   */
  public static FormModel createSingleDocumentFormModel(TextDocumentModel doc)
      throws InvalidFormDescriptorException
  {

    // Abschnitt "Formular" holen:
    ConfigThingy formConf = new ConfigThingy("");
    try
    {
      formConf = doc.getFormDescription().get("Formular");
    }
    catch (NodeNotFoundException e)
    {
      throw new InvalidFormDescriptorException(
          "Kein Abschnitt 'Formular' in der Formularbeschreibung vorhanden");
    }

    // FunctionContext erzeugen und im Formular definierte Funktionen /
    // DialogFunktionen parsen:
    Map functionContext = new HashMap();
    DialogLibrary dialogLib = new DialogLibrary();
    FunctionLibrary funcLib = new FunctionLibrary();
    WollMuxSingleton mux = WollMuxSingleton.getInstance();
    dialogLib = WollMuxFiles.parseFunctionDialogs(formConf, mux
        .getFunctionDialogs(), functionContext);
    funcLib = WollMuxFiles.parseFunctions(
        formConf,
        dialogLib,
        functionContext,
        mux.getGlobalFunctions());

    // Abschnitt Fenster/Formular aus wollmuxConf holen:
    ConfigThingy formFensterConf = new ConfigThingy("");
    try
    {
      formFensterConf = WollMuxFiles.getWollmuxConf().query("Fenster").query(
          "Formular").getLastChild();
    }
    catch (NodeNotFoundException x)
    {
    }

    return new FormModelImpl.SingleDocumentFormModel(doc, formFensterConf,
        formConf, functionContext, funcLib, dialogLib);
  }

  /**
   * Erzeugt ein FormModel dem mehrere Formulardokumente zugeordnet sind, die
   * alle in gleicher Weise �ber �nderungen (z.B. bei valueChanged()) informiert
   * werden. So ist ein gleichzeitiges Bef�llen meherer Dokumente �ber nur ein
   * Formular m�glich. Die Formularbeschreibungen der Einzeldokumente und die in
   * ihnen enthaltenen IDs, Funktionen und Dialogfunktionen werden dabei zu
   * einer Gesamtbeschreibung im selben Namensraum zusammengemerged.
   * 
   * @param docs
   *          Ein Vector mit TextDocumentModel Objekten die dem neuen
   *          MultiDocumentFormModel zugeordnet werden sollen.
   * @return Ein FormModel, das alle �nderungen auf alle in docs enthaltenen
   *         Formulardokumente �bertr�gt.
   * @throws InvalidFormDescriptorException
   */
  public static FormModel createMultiDocumentFormModel(
      Vector /* of TextDocumentModel */docs)
      throws InvalidFormDescriptorException
  {

    // Formular-Abschnitte aller TextDocumentModels sammeln...
    ArrayList formularSections = new ArrayList();
    for (Iterator iter = docs.iterator(); iter.hasNext();)
    {
      TextDocumentModel model = (TextDocumentModel) iter.next();
      try
      {
        ConfigThingy formular = model.getFormDescription().get("Formular");
        formularSections.add(formular);
      }
      catch (NodeNotFoundException e)
      {
        Logger.error("Dokument '"
                     + model.getTitle()
                     + "' enth�lt keine g�ltige Formularbeschreibung", e);
      }
    }

    // ...und mergen
    ConfigThingy formConf = FormController
        .mergeFormDescriptors(formularSections);

    // mapIdToPresetValue aller Einzeldokumente vereinheitlichen:
    HashMap commonMapIdToPresetValue = new HashMap();
    for (Iterator iter = docs.iterator(); iter.hasNext();)
    {
      TextDocumentModel doc = (TextDocumentModel) iter.next();
      HashMap myIdToPresetValue = doc.getIDToPresetValue();
      Iterator piter = myIdToPresetValue.keySet().iterator();
      while (piter.hasNext())
      {
        String id = piter.next().toString();
        String myPresetValue = "" + myIdToPresetValue.get(id);
        String commonPresetValue = (String) commonMapIdToPresetValue.get(id);
        if (commonPresetValue == null)
        {
          commonMapIdToPresetValue.put(id, myPresetValue);
        }
        else if (!commonPresetValue.equals(myPresetValue))
        {
          commonMapIdToPresetValue.put(id, FormController.FISHY);
        }
      }
    }

    // FunctionContext erzeugen und im Formular definierte
    // Funktionen/DialogFunktionen parsen:
    Map functionContext = new HashMap();
    DialogLibrary dialogLib = new DialogLibrary();
    FunctionLibrary funcLib = new FunctionLibrary();
    WollMuxSingleton mux = WollMuxSingleton.getInstance();
    dialogLib = WollMuxFiles.parseFunctionDialogs(formConf, mux
        .getFunctionDialogs(), functionContext);
    funcLib = WollMuxFiles.parseFunctions(
        formConf,
        dialogLib,
        functionContext,
        mux.getGlobalFunctions());

    // Abschnitt Fenster/Formular aus wollmuxConf holen:
    ConfigThingy formFensterConf = new ConfigThingy("");
    try
    {
      formFensterConf = WollMuxFiles.getWollmuxConf().query("Fenster").query(
          "Formular").getLastChild();
    }
    catch (NodeNotFoundException x)
    {
    }

    // FormModels f�r die Einzeldokumente erzeugen
    HashMap mapDocsToFormModels = new HashMap();
    for (Iterator iter = docs.iterator(); iter.hasNext();)
    {
      TextDocumentModel doc = (TextDocumentModel) iter.next();
      FormModel fm = new FormModelImpl.SingleDocumentFormModel(doc,
          formFensterConf, formConf, functionContext, funcLib, dialogLib);
      mapDocsToFormModels.put(doc, fm);
    }

    return new FormModelImpl.MultiDocumentFormModel(mapDocsToFormModels,
        formFensterConf, formConf, functionContext, commonMapIdToPresetValue,
        funcLib, dialogLib);
  }

  /**
   * Exception die eine ung�ltige Formularbeschreibung eines Formulardokuments
   * repr�sentiert.
   * 
   * @author christoph.lutz
   */
  public static class InvalidFormDescriptorException extends Exception
  {
    private static final long serialVersionUID = -4636262921405770907L;

    public InvalidFormDescriptorException(String message)
    {
      super(message);
    }
  }

  /**
   * Repr�sentiert ein FormModel dem mehrere Formulardokumente zugeordnet sind,
   * die alle in gleicher Weise �ber �nderungen (z.B. bei valueChanged())
   * informiert werden. So ist ein gleichzeitiges Bef�llen meherer Dokumente
   * �ber nur ein Formular m�glich. Die Formularbeschreibungen der
   * Einzeldokumente und die in ihnen enthaltenen IDs, Funktionen und
   * Dialogfunktionen m�ssen dabei vor dem Erzeugen des Objekts zu einer
   * Gesamtbeschreibung zusammengemerged werden.
   * 
   * @author christoph.lutz
   */
  private static class MultiDocumentFormModel implements FormModel
  {
    private HashMap mapDocsToFormModels;

    private final ConfigThingy formFensterConf;

    private final ConfigThingy formConf;

    private final Map functionContext;

    private final FunctionLibrary funcLib;

    private final DialogLibrary dialogLib;

    private final HashMap commonMapIdToPresetValue;

    private FormGUI formGUI = null;

    /**
     * Konstruktor f�r ein MultiDocumentFormModel mit den zugeh�rigen
     * TextDocumentModel Objekten, die in einer HashMap abgelegt sind, die eine
     * Zuordnung von TextDocumentModel auf ein FormModel (�blicherweise Objekte
     * vom Typ SingleDocumentFormModel) enthalten. Das MultiDocumentFormModel
     * leitet alle Anfragen an die mitgelieferten FormModel Objekte weiter.
     * 
     * @param mapDocsToFormModels
     *          enth�lt die zugeordneten TextDocumentModels.
     * @param formFensterConf
     *          Der Formular-Unterabschnitt des Fenster-Abschnitts von
     *          wollmux.conf (wird f�r createFormGUI() ben�tigt).
     * @param formConf
     *          der Formular-Knoten, der die Formularbeschreibung enth�lt (wird
     *          f�r createFormGUI() ben�tigt).
     * @param commonMapIdToPresetValue
     *          bildet IDs von Formularfeldern auf Vorgabewerte ab. Falls hier
     *          ein Wert f�r ein Formularfeld vorhanden ist, so wird dieser
     *          allen anderen automatischen Bef�llungen vorgezogen. Wird das
     *          Objekt {@link FormController#FISHY} als Wert f�r ein Feld
     *          �bergeben, so wird dieses Feld speziell markiert als ung�ltig
     *          bis der Benutzer es manuell �ndert (wird f�r createFormGUI()
     *          ben�tigt).
     * @param functionContext
     *          der Kontext f�r Funktionen, die einen ben�tigen (wird f�r
     *          createFormGUI() ben�tigt).
     * @param funcLib
     *          die Funktionsbibliothek, die zur Auswertung von Trafos, Plausis
     *          etc. herangezogen werden soll.
     * @param dialogLib
     *          die Dialogbibliothek, die die Dialoge bereitstellt, die f�r
     *          automatisch zu bef�llende Formularfelder ben�tigt werden (wird
     *          f�r createFormGUI() ben�tigt).
     */
    public MultiDocumentFormModel(HashMap mapDocsToFormModels,
        final ConfigThingy formFensterConf, final ConfigThingy formConf,
        final Map functionContext, final HashMap commonMapIdToPresetValue,
        final FunctionLibrary funcLib, final DialogLibrary dialogLib)
    {
      this.mapDocsToFormModels = mapDocsToFormModels;
      this.formFensterConf = formFensterConf;
      this.formConf = formConf;
      this.functionContext = functionContext;
      this.commonMapIdToPresetValue = commonMapIdToPresetValue;
      this.funcLib = funcLib;
      this.dialogLib = dialogLib;

    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setWindowPosSize(int, int,
     *      int, int)
     */
    public void setWindowPosSize(int docX, int docY, int docWidth, int docHeight)
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.setWindowPosSize(docX, docY, docWidth, docHeight);
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setWindowVisible(boolean)
     */
    public void setWindowVisible(boolean vis)
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.setWindowVisible(vis);
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#close()
     */
    public void close()
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.close();
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setVisibleState(java.lang.String,
     *      boolean)
     */
    public void setVisibleState(String groupId, boolean visible)
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.setVisibleState(groupId, visible);
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#valueChanged(java.lang.String,
     *      java.lang.String)
     */
    public void valueChanged(String fieldId, String newValue)
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.valueChanged(fieldId, newValue);
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#focusGained(java.lang.String)
     */
    public void focusGained(String fieldId)
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.focusGained(fieldId);
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#focusLost(java.lang.String)
     */
    public void focusLost(String fieldId)
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.focusLost(fieldId);
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#print()
     */
    public void print()
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.print();
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#pdf()
     */
    public void pdf()
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);
        fm.pdf();
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#disposing(de.muenchen.allg.itd51.wollmux.TextDocumentModel)
     * 
     * TESTED
     */
    public void disposing(TextDocumentModel source)
    {
      for (Iterator iter = mapDocsToFormModels.keySet().iterator(); iter
          .hasNext();)
      {
        TextDocumentModel doc = (TextDocumentModel) iter.next();
        FormModel fm = (FormModel) mapDocsToFormModels.get(doc);

        if (doc.equals(source))
        {
          fm.disposing(source);
          iter.remove();
        }
      }

      // FormGUI beenden (falls bisher eine gesetzt ist)
      if (mapDocsToFormModels.size() == 0 && formGUI != null)
      {
        formGUI.dispose();
        formGUI = null;
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setValue(java.lang.String,
     *      java.lang.String, java.awt.event.ActionListener)
     */
    public void setValue(String fieldId, String value, ActionListener listener)
    {
      if (formGUI != null)
        formGUI.getController().setValue(fieldId, value, listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#startFormGUI()
     */
    public void startFormGUI()
    {
      formGUI = new FormGUI(formFensterConf, formConf, this,
          commonMapIdToPresetValue, functionContext, funcLib, dialogLib);
    }
  }

  /**
   * Repr�sentiert ein FormModel f�r ein einfaches Formular mit genau einem
   * zugeh�rigen Formulardokument. Diese Klasse sorgt als Wrapper im
   * Wesentlichen nur daf�r, dass alle Methodenaufrufe des FormModels in die
   * ensprechenden WollMuxEvents verpackt werden und somit korrekt
   * synchronisiert ausgef�hrt werden.
   */
  private static class SingleDocumentFormModel implements FormModel
  {
    private final TextDocumentModel doc;

    private final ConfigThingy formFensterConf;

    private final ConfigThingy formConf;

    private final Map functionContext;

    private final FunctionLibrary funcLib;

    private final DialogLibrary dialogLib;

    private final String defaultWindowAttributes;

    private FormGUI formGUI = null;

    /**
     * Konstruktor f�r ein SingleDocumentFormModel mit dem zugeh�rigen
     * TextDocumentModel doc.
     * 
     * @param doc
     *          Das zugeordnete TextDocumentModel.
     * @param formFensterConf
     *          Der Formular-Unterabschnitt des Fenster-Abschnitts von
     *          wollmux.conf (wird f�r createFormGUI() ben�tigt).
     * @param formConf
     *          der Formular-Knoten, der die Formularbeschreibung enth�lt (wird
     *          f�r createFormGUI() ben�tigt).
     * @param functionContext
     *          der Kontext f�r Funktionen, die einen ben�tigen (wird f�r
     *          createFormGUI() ben�tigt).
     * @param funcLib
     *          die Funktionsbibliothek, die zur Auswertung von Trafos, Plausis
     *          etc. herangezogen werden soll.
     * @param dialogLib
     *          die Dialogbibliothek, die die Dialoge bereitstellt, die f�r
     *          automatisch zu bef�llende Formularfelder ben�tigt werden (wird
     *          f�r createFormGUI() ben�tigt).
     */
    public SingleDocumentFormModel(final TextDocumentModel doc,
        final ConfigThingy formFensterConf, final ConfigThingy formConf,
        final Map functionContext, final FunctionLibrary funcLib,
        final DialogLibrary dialogLib)
    {
      this.doc = doc;
      this.formFensterConf = formFensterConf;
      this.formConf = formConf;
      this.functionContext = functionContext;
      this.funcLib = funcLib;
      this.dialogLib = dialogLib;

      // Standard-Fensterattribute vor dem Start der Form-GUI sichern um nach
      // dem Schlie�en des Formulardokuments die Standard-Werte wieder
      // herstellen zu k�nnen. Die Standard-Attribute �ndern sich (OOo-seitig)
      // immer dann, wenn ein Dokument (mitsamt Fenster) geschlossen wird. Dann
      // merkt sich OOo die Position und Gr��e des zuletzt geschlossenen
      // Fensters.
      this.defaultWindowAttributes = getDefaultWindowAttributes();
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#close()
     */
    public void close()
    {
      UNO.dispatch(doc.doc, ".uno:CloseDoc");
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setWindowVisible(boolean)
     */
    public void setWindowVisible(boolean vis)
    {
      WollMuxEventHandler.handleSetWindowVisible(doc, vis);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setWindowPosSize(int, int,
     *      int, int)
     */
    public void setWindowPosSize(int docX, int docY, int docWidth, int docHeight)
    {
      WollMuxEventHandler.handleSetWindowPosSize(
          doc,
          docX,
          docY,
          docWidth,
          docHeight);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setVisibleState(java.lang.String,
     *      boolean)
     */
    public void setVisibleState(String groupId, boolean visible)
    {
      WollMuxEventHandler.handleSetVisibleState(doc, groupId, visible);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#valueChanged(java.lang.String,
     *      java.lang.String)
     */
    public void valueChanged(String fieldId, String newValue)
    {
      if (fieldId.length() > 0)
        WollMuxEventHandler.handleFormValueChanged(
            doc,
            fieldId,
            newValue,
            funcLib);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#focusGained(java.lang.String)
     */
    public void focusGained(String fieldId)
    {
      WollMuxEventHandler.handleFocusFormField(doc, fieldId);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#focusLost(java.lang.String)
     */
    public void focusLost(String fieldId)
    {
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#disposed(de.muenchen.allg.itd51.wollmux.TextDocumentModel)
     */
    public void disposing(TextDocumentModel source)
    {
      if (doc.equals(source))
      {
        if (formGUI != null)
        {
          formGUI.dispose();
          formGUI = null;
        }

        // R�cksetzen des defaultWindowAttributes auf den Wert vor dem Schlie�en
        // des Formulardokuments.
        if (defaultWindowAttributes != null)
          setDefaultWindowAttributes(defaultWindowAttributes);
      }
    }

    /**
     * Diese Hilfsmethode liest das Attribut ooSetupFactoryWindowAttributes aus
     * dem Konfigurationsknoten
     * "/org.openoffice.Setup/Office/Factories/com.sun.star.text.TextDocument"
     * der OOo-Konfiguration, welches die Standard-FensterAttribute enth�lt, mit
     * denen neue Fenster f�r TextDokumente erzeugt werden.
     * 
     * @return
     */
    private static String getDefaultWindowAttributes()
    {
      try
      {
        Object cp = UNO
            .createUNOService("com.sun.star.configuration.ConfigurationProvider");

        // creation arguments: nodepath
        com.sun.star.beans.PropertyValue aPathArgument = new com.sun.star.beans.PropertyValue();
        aPathArgument.Name = "nodepath";
        aPathArgument.Value = "/org.openoffice.Setup/Office/Factories/com.sun.star.text.TextDocument";
        Object[] aArguments = new Object[1];
        aArguments[0] = aPathArgument;

        Object ca = UNO.XMultiServiceFactory(cp).createInstanceWithArguments(
            "com.sun.star.configuration.ConfigurationAccess",
            aArguments);

        return UNO.getProperty(ca, "ooSetupFactoryWindowAttributes").toString();
      }
      catch (java.lang.Exception e)
      {
      }
      return null;
    }

    /**
     * Diese Hilfsmethode setzt das Attribut ooSetupFactoryWindowAttributes aus
     * dem Konfigurationsknoten
     * "/org.openoffice.Setup/Office/Factories/com.sun.star.text.TextDocument"
     * der OOo-Konfiguration auf den neuen Wert value, der (am besten) �ber
     * einen vorhergehenden Aufruf von getDefaultWindowAttributes() gewonnen
     * wird.
     * 
     * @param value
     */
    private static void setDefaultWindowAttributes(String value)
    {
      try
      {
        Object cp = UNO
            .createUNOService("com.sun.star.configuration.ConfigurationProvider");

        // creation arguments: nodepath
        com.sun.star.beans.PropertyValue aPathArgument = new com.sun.star.beans.PropertyValue();
        aPathArgument.Name = "nodepath";
        aPathArgument.Value = "/org.openoffice.Setup/Office/Factories/com.sun.star.text.TextDocument";
        Object[] aArguments = new Object[1];
        aArguments[0] = aPathArgument;

        Object ca = UNO.XMultiServiceFactory(cp).createInstanceWithArguments(
            "com.sun.star.configuration.ConfigurationUpdateAccess",
            aArguments);

        UNO.setProperty(ca, "ooSetupFactoryWindowAttributes", value);

        UNO.XChangesBatch(ca).commitChanges();
      }
      catch (java.lang.Exception e)
      {
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#print()
     */
    public void print()
    {
      UNO.dispatch(doc.doc, DispatchHandler.DISP_unoPrint);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#pdf()
     */
    public void pdf()
    {
      UNO.dispatch(doc.doc, ".uno:ExportToPDF");
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#setValue(java.lang.String,
     *      java.lang.String, java.awt.event.ActionListener)
     */
    public void setValue(String fieldId, String value, ActionListener listener)
    {
      if (formGUI != null)
        formGUI.getController().setValue(fieldId, value, listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.muenchen.allg.itd51.wollmux.FormModel#startFormGUI()
     */
    public void startFormGUI()
    {
      formGUI = new FormGUI(formFensterConf, formConf, this, doc
          .getIDToPresetValue(), functionContext, funcLib, dialogLib);
    }
  }
}