/*
 * Dateiname: SachleitendeVerfuegung.java
 * Projekt  : WollMux
 * Funktion : Hilfen f�r Sachleitende Verf�gungen.
 * 
 * Copyright (c) 2008 Landeshauptstadt M�nchen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the European Union Public Licence (EUPL),
 * version 1.0.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * European Union Public Licence for more details.
 *
 * You should have received a copy of the European Union Public Licence
 * along with this program. If not, see
 * http://ec.europa.eu/idabc/en/document/7330
 *
 * �nderungshistorie:
 * Datum      | Wer | �nderungsgrund
 * -------------------------------------------------------------------
 * 26.09.2006 | LUT | Erstellung als SachleitendeVerfuegung
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.star.awt.FontWeight;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNameContainer;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.style.XStyle;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextFrame;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextRangeCompare;
import com.sun.star.text.XTextSection;
import com.sun.star.text.XTextSectionsSupplier;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.AnyConverter;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.parser.ConfigThingy;
import de.muenchen.allg.itd51.parser.NodeNotFoundException;
import de.muenchen.allg.itd51.wollmux.dialog.SachleitendeVerfuegungenDruckdialog;
import de.muenchen.allg.itd51.wollmux.dialog.SachleitendeVerfuegungenDruckdialog.VerfuegungspunktInfo;

public class SachleitendeVerfuegung
{
  public static final String BLOCKNAME_SLV_ALL_VERSIONS = "AllVersions";

  public static final String BLOCKNAME_SLV_ORIGINAL_ONLY = "OriginalOnly";

  public static final String BLOCKNAME_SLV_NOT_IN_ORIGINAL = "NotInOriginal";

  public static final String BLOCKNAME_SLV_DRAFT_ONLY = "DraftOnly";

  public static final String GROUP_ID_SLV_ALL_VERSIONS =
    "SLV_" + BLOCKNAME_SLV_ALL_VERSIONS;

  public static final String GROUP_ID_SLV_ORIGINAL_ONLY =
    "SLV_" + BLOCKNAME_SLV_ORIGINAL_ONLY;

  public static final String GROUP_ID_SLV_NOT_IN_ORIGINAL =
    "SLV_" + BLOCKNAME_SLV_NOT_IN_ORIGINAL;

  public static final String GROUP_ID_SLV_DRAFT_ONLY =
    "SLV_" + BLOCKNAME_SLV_DRAFT_ONLY;

  public static final String PRINT_FUNCTION_NAME = "SachleitendeVerfuegung";

  private static final String CHARACTER_STYLES = "CharacterStyles";

  private static final String PARAGRAPH_STYLES = "ParagraphStyles";

  private static final String ParaStyleNameVerfuegungspunkt =
    "WollMuxVerfuegungspunkt";

  private static final String ParaStyleNameVerfuegungspunkt1 =
    "WollMuxVerfuegungspunkt1";

  private static final String ParaStyleNameAbdruck =
    "WollMuxVerfuegungspunktAbdruck";

  private static final String ParaStyleNameVerfuegungspunktMitZuleitung =
    "WollMuxVerfuegungspunktMitZuleitung";

  private static final String ParaStyleNameZuleitungszeile =
    "WollMuxZuleitungszeile";

  private static final String ParaStyleNameDefault = "Flie�text";

  private static final String CharStyleNameDefault = "Flie�text";

  private static final String CharStyleNameRoemischeZiffer =
    "WollMuxRoemischeZiffer";

  private static final String FrameNameVerfuegungspunkt1 =
    "WollMuxVerfuegungspunkt1";

  private static final String zifferPattern = "^([XIV]+|\\d+)\\.\t";

  /**
   * Enth�lt einen Vector mit den ersten 15 r�mischen Ziffern. Mehr wird in
   * Sachleitenden Verf�gungen sicherlich nicht ben�tigt :-)
   */
  private static final String[] romanNumbers =
    new String[] {
      "I.", "II.", "III.", "IV.", "V.", "VI.", "VII.", "VIII.", "IX.", "X.", "XI.",
      "XII.", "XIII.", "XIV.", "XV." };

  /**
   * Setzt das Absatzformat des Absatzes, der range ber�hrt, auf
   * "WollMuxVerfuegungspunkt" ODER setzt alle in range enthaltenen Verf�gungspunkte
   * auf Flie�text zur�ck, wenn range einen oder mehrere Verf�gungspunkte ber�hrt.
   * 
   * @param range
   *          Die XTextRange, in der sich zum Zeitpunkt des Aufrufs der Cursor
   *          befindet.
   * @return die Position zur�ck, auf die der ViewCursor gesetzt werden soll oder
   *         null, falls der ViewCursor unver�ndert bleibt.
   */
  public static XTextRange insertVerfuegungspunkt(TextDocumentModel model,
      XTextRange range)
  {
    if (range == null) return null;

    // Notwendige Absatzformate definieren (falls nicht bereits definiert)
    createUsedStyles(model.doc);

    XParagraphCursor cursor =
      UNO.XParagraphCursor(range.getText().createTextCursorByRange(range));

    // Enth�lt der markierte Bereich bereits Verfuegungspunkte, so werden diese
    // gel�scht
    boolean deletedAtLeastOne = removeAllVerfuegungspunkte(cursor);

    if (!deletedAtLeastOne)
    {
      // neuen Verf�gungspunkt setzen:
      cursor.collapseToStart();
      cursor.gotoStartOfParagraph(false);
      cursor.gotoEndOfParagraph(true);
      if (isZuleitungszeile(cursor))
        formatVerfuegungspunktWithZuleitung(cursor);
      else
        formatVerfuegungspunkt(cursor);
    }

    // Ziffernanpassung durchf�hren:
    ziffernAnpassen(model);

    return null;
  }

  /**
   * Erzeugt am Ende des Paragraphen, der von range ber�hrt wird, einen neuen
   * Paragraphen, setzt diesen auf das Absatzformat WollMuxVerfuegungspunktAbdruck
   * und belegt ihn mit dem String "Abdruck von <Vorg�nger>" ODER l�scht alle
   * Verf�gungspunkte die der range ber�hrt, wenn in ihm mindestens ein bereits
   * bestehender Verf�gungspunkt enthalten ist.
   * 
   * @param doc
   *          Das Dokument, in dem der Verf�gungspunkt eingef�gt werden soll (wird
   *          f�r die Ziffernanpassung ben�tigt)
   * @param cursor
   *          Der Cursor, in dessen Bereich nach Verf�gungspunkten gesucht wird.
   * @return die Position zur�ck, auf die der ViewCursor gesetzt werden soll oder
   *         null, falls der ViewCursor unver�ndert bleibt.
   */
  public static XTextRange insertAbdruck(TextDocumentModel model, XTextRange range)
  {
    if (range == null) return null;

    // Notwendige Absatzformate definieren (falls nicht bereits definiert)
    createUsedStyles(model.doc);

    XParagraphCursor cursor =
      UNO.XParagraphCursor(range.getText().createTextCursorByRange(range));

    // Enth�lt der markierte Bereich bereits Verfuegungspunkte, so werden diese
    // gel�scht
    boolean deletedAtLeastOne = removeAllAbdruecke(cursor);

    if (!deletedAtLeastOne)
    {
      // Abdruck einf�gen, wenn kein Verf�gungspunkt gel�scht wurde:

      // Startposition des cursors setzen. Bereiche werden auf den Anfang
      // kollabiert. Bei Verf�gungspunkten wird am Absatzende eingef�gt.
      cursor.collapseToStart();
      if (isVerfuegungspunkt(cursor)) cursor.gotoEndOfParagraph(false);

      int count = countVerfPunkteBefore(model.doc, cursor) + 1;
      cursor.setString("\r" + abdruckString(count) + "\r");
      // Falls Cursor auf dem Zeilenanfang stand, wird die Formatierung auf
      // Standardformatierung gesetzt
      if (cursor.isStartOfParagraph()) formatDefault(cursor.getStart());
      cursor.gotoNextParagraph(false);
      cursor.gotoEndOfParagraph(true);
      formatAbdruck(cursor);
      cursor.gotoNextParagraph(false);
    }

    // Ziffern anpassen:
    ziffernAnpassen(model);

    return cursor;
  }

  /**
   * Formatiert alle Paragraphen die der TextRange range ber�hrt mit dem Absatzformat
   * WollMuxZuleitungszeile und markiert diese Zeilen damit auch semantisch als
   * Zuleitungszeilen ODER setzt das Absatzformat der ensprechenden Paragraphen
   * wieder auf Flie�text zur�ck, wenn mindestens ein Paragraph bereits eine
   * Zuleitungszeile ist.
   * 
   * @param doc
   *          Das Dokument in dem die sich range befindet.
   * @param range
   * @return die Position zur�ck, auf die der ViewCursor gesetzt werden soll oder
   *         null, falls der ViewCursor unver�ndert bleibt.
   */
  public static XTextRange insertZuleitungszeile(TextDocumentModel model,
      XTextRange range)
  {
    if (range == null) return null;

    // Notwendige Absatzformate definieren (falls nicht bereits definiert)
    createUsedStyles(model.doc);

    XParagraphCursor cursor =
      UNO.XParagraphCursor(range.getText().createTextCursorByRange(range));
    XTextCursor createdZuleitung = null;

    boolean deletedAtLeastOne = removeAllZuleitungszeilen(cursor);

    if (!deletedAtLeastOne && UNO.XEnumerationAccess(cursor) != null)
    {
      // Im cursor enthaltene Paragraphen einzeln iterieren und je nach Typ
      // entweder eine Zuleitungszeile oder einen Verf�gungspunkt mit Zuleitung
      // setzen.
      XEnumeration paragraphs = UNO.XEnumerationAccess(cursor).createEnumeration();
      while (paragraphs.hasMoreElements())
      {
        XTextRange par = null;
        try
        {
          par = UNO.XTextRange(paragraphs.nextElement());
        }
        catch (java.lang.Exception e)
        {}

        if (par != null)
        {
          if (isAbdruck(par))
          {
            if (cursor.isCollapsed()) // Ignorieren, wenn Bereich ausgew�hlt.
            {
              // Zuleitung in neuer Zeile erzeugen:
              par.getEnd().setString("\r");
              createdZuleitung = par.getText().createTextCursorByRange(par.getEnd());
              if (createdZuleitung != null)
              {
                createdZuleitung.goRight((short) 1, false);
                formatZuleitungszeile(createdZuleitung);
              }
            }
          }
          else if (isVerfuegungspunkt(par))
            formatVerfuegungspunktWithZuleitung(par);
          else
            formatZuleitungszeile(par);
        }
      }
    }
    return createdZuleitung;
  }

  /**
   * Diese Methode l�scht alle Verf�gungspunkte, die der Bereich des Cursors cursor
   * ber�hrt, und liefert true zur�ck, wenn mindestens ein Verf�gungspunkt gel�scht
   * wurde oder false, wenn sich in dem Bereich des Cursors kein Verf�gungspunkt
   * befand.
   * 
   * @param cursor
   *          Der Cursor, in dessen Bereich nach Verf�gungspunkten gesucht wird.
   * 
   * @return true, wenn mindestens ein Verf�gungspunkt gel�scht wurde oder false,
   *         wenn kein der cursor keinen Verf�gungspunkt ber�hrt.
   */
  private static boolean removeAllVerfuegungspunkte(XParagraphCursor cursor)
  {
    boolean deletedAtLeastOne = false;
    if (UNO.XEnumerationAccess(cursor) != null)
    {
      XEnumeration xenum = UNO.XEnumerationAccess(cursor).createEnumeration();

      while (xenum.hasMoreElements())
      {
        XTextRange par = null;
        try
        {
          par = UNO.XTextRange(xenum.nextElement());
        }
        catch (java.lang.Exception e)
        {
          Logger.error(e);
        }

        if (par != null)
        {
          boolean isVerfuegungspunktMitZuleitung =
            isVerfuegungspunktMitZuleitung(par);
          if (isVerfuegungspunkt(par))
          {
            // Einen evtl. bestehenden Verfuegungspunkt zur�cksetzen
            removeSingleVerfuegungspunkt(par);
            deletedAtLeastOne = true;
          }
          if (isVerfuegungspunktMitZuleitung) formatZuleitungszeile(par);
        }
      }
    }
    return deletedAtLeastOne;
  }

  /**
   * Diese Methode l�scht alle Abdruck-Zeilen, die der Bereich des Cursors cursor
   * ber�hrt, und liefert true zur�ck, wenn mindestens ein Abdruck gel�scht wurde
   * oder false, wenn sich in dem Bereich des Cursors kein Abdruck befand.
   * 
   * @param cursor
   *          Der Cursor, in dessen Bereich nach Abdr�cken gesucht wird.
   * 
   * @return true, wenn mindestens ein Abdruck gel�scht wurde oder false, wenn kein
   *         der cursor keinen Verf�gungspunkt ber�hrt.
   */
  private static boolean removeAllAbdruecke(XParagraphCursor cursor)
  {
    boolean deletedAtLeastOne = false;
    if (UNO.XEnumerationAccess(cursor) != null)
    {
      XEnumeration xenum = UNO.XEnumerationAccess(cursor).createEnumeration();

      while (xenum.hasMoreElements())
      {
        XTextRange par = null;
        try
        {
          par = UNO.XTextRange(xenum.nextElement());
        }
        catch (java.lang.Exception e)
        {
          Logger.error(e);
        }

        if (par != null)
        {
          if (isAbdruck(par))
          {
            // Einen evtl. bestehenden Verfuegungspunkt zur�cksetzen
            removeSingleVerfuegungspunkt(par);
            deletedAtLeastOne = true;
          }
        }
      }
    }
    return deletedAtLeastOne;
  }

  /**
   * Diese Methode l�scht alle Zuleitungszeilen, die der Bereich des Cursors cursor
   * ber�hrt, und liefert true zur�ck, wenn mindestens eine Zuleitungszeile gel�scht
   * wurde oder false, wenn sich in dem Bereich des Cursors keine Zuleitungszeile
   * befand.
   * 
   * @param cursor
   *          Der Cursor, in dessen Bereich nach Zuleitungszeilen gesucht wird.
   * 
   * @return true, wenn mindestens eine Zuleitungszeile gel�scht wurde oder false,
   *         wenn kein der cursor keine Zuleitungszeile ber�hrt.
   */
  private static boolean removeAllZuleitungszeilen(XParagraphCursor cursor)
  {
    boolean deletedAtLeastOne = false;
    if (UNO.XEnumerationAccess(cursor) != null)
    {
      XEnumeration xenum = UNO.XEnumerationAccess(cursor).createEnumeration();

      while (xenum.hasMoreElements())
      {
        XTextRange par = null;
        try
        {
          par = UNO.XTextRange(xenum.nextElement());
        }
        catch (java.lang.Exception e)
        {
          Logger.error(e);
        }

        if (par != null)
        {
          if (isZuleitungszeile(par))
          {
            // Zuleitungszeile zur�cksetzen
            formatDefault(par);
            deletedAtLeastOne = true;
          }
          else if (isVerfuegungspunktMitZuleitung(par))
          {
            // Zuleitung aus Verf�gungspunkt entfernen:
            formatVerfuegungspunkt(par);
            deletedAtLeastOne = true;
          }
        }
      }
    }
    return deletedAtLeastOne;
  }

  /**
   * L�scht die r�mische Ziffer+PUNKT+Tab aus einem als
   * "WollMuxVerfuegungspunkt[...]" markierten Absatz heraus und setzt das
   * Absatzformat auf "Flie�text" zur�ck.
   * 
   * @param par
   *          der Cursor, der sich in der entsprechenden Zeile befinden muss.
   */
  private static void removeSingleVerfuegungspunkt(XTextRange par)
  {
    formatDefault(par);

    // Pr�fe, ob der Absatz mit einer r�mischen Ziffer beginnt.
    XTextCursor zifferOnly = getZifferOnly(par, false);

    // r�mische Ziffer l�schen.
    if (zifferOnly != null) zifferOnly.setString("");

    // wenn es sich bei dem Paragraphen um einen Abdruck handelt, wird dieser
    // vollst�ndig gel�scht.
    if (isAbdruck(par))
    {
      // Den Absatz mit dem String "Ziffer.\tAbdruck von..." l�schen
      par.setString("");

      XParagraphCursor parDeleter =
        UNO.XParagraphCursor(par.getText().createTextCursorByRange(par.getEnd()));

      // L�scht das Leerzeichen vor dem Abdruck und nach dem Abdruck (falls eine
      // Leerzeile folgt)
      parDeleter.goLeft((short) 1, true);
      parDeleter.setString("");
      if (parDeleter.goRight((short) 1, false) && parDeleter.isEndOfParagraph())
      {
        parDeleter.goLeft((short) 1, true);
        parDeleter.setString("");
      }
    }
  }

  /**
   * Formatiert den �bergebenen Absatz paragraph in der Standardschriftart
   * "Flie�text".
   * 
   * @param paragraph
   */
  private static void formatDefault(XTextRange paragraph)
  {
    UNO.setProperty(paragraph, "ParaStyleName", ParaStyleNameDefault);
    formatRoemischeZifferOnly(paragraph);
  }

  /**
   * Formatiert den �bergebenen Absatz paragraph als Abdruck.
   * 
   * @param paragraph
   */
  private static void formatAbdruck(XTextRange paragraph)
  {
    UNO.setProperty(paragraph, "ParaStyleName", ParaStyleNameAbdruck);
    formatRoemischeZifferOnly(paragraph);
  }

  /**
   * Formatiert den �bergebenen Absatz paragraph als Verf�gungspunkt mit
   * Zuleitungszeile.
   * 
   * @param paragraph
   */
  private static void formatVerfuegungspunktWithZuleitung(XTextRange paragraph)
  {
    UNO.setProperty(paragraph, "ParaStyleName",
      ParaStyleNameVerfuegungspunktMitZuleitung);
    formatRoemischeZifferOnly(paragraph);
  }

  /**
   * Formatiert den �bergebenen Absatz paragraph als Verf�gungspunkt.
   * 
   * @param paragraph
   */
  private static void formatVerfuegungspunkt(XTextRange paragraph)
  {
    UNO.setProperty(paragraph, "ParaStyleName", ParaStyleNameVerfuegungspunkt);
    formatRoemischeZifferOnly(paragraph);
  }

  /**
   * Formatiert den �bergebenen Absatz paragraph als Zuleitungszeile.
   * 
   * @param paragraph
   */
  private static void formatZuleitungszeile(XTextRange paragraph)
  {
    UNO.setProperty(paragraph, "ParaStyleName", ParaStyleNameZuleitungszeile);
    formatRoemischeZifferOnly(paragraph);
  }

  /**
   * Holt sich aus dem �bergebenen Absatz paragraph nur den Breich der r�mischen
   * Ziffer (+Tab) und formatiert diesen im Zeichenformat WollMuxRoemischeZiffer.
   * 
   * @param paragraph
   */
  private static void formatRoemischeZifferOnly(XTextRange paragraph)
  {
    XTextCursor zifferOnly = getZifferOnly(paragraph, false);
    if (zifferOnly != null)
    {
      UNO.setProperty(zifferOnly, "CharStyleName", CharStyleNameRoemischeZiffer);

      // Zeichen danach auf Standardformatierung setzen, damit der Text, der
      // danach geschrieben wird nicht auch obiges Zeichenformat besitzt:
      // ("Standard" gilt laut DevGuide auch in englischen Versionen)
      UNO.setProperty(zifferOnly.getEnd(), "CharStyleName", "Standard");
    }
  }

  /**
   * Liefert true, wenn es sich bei dem �bergebenen Absatz paragraph um einen als
   * Verfuegungspunkt markierten Absatz handelt.
   * 
   * @param paragraph
   *          Das Objekt mit der Property ParaStyleName, die f�r den Vergleich
   *          herangezogen wird.
   * @return true, wenn der Name des Absatzformates mit "WollMuxVerfuegungspunkt"
   *         beginnt.
   */
  private static boolean isVerfuegungspunkt(XTextRange paragraph)
  {
    String paraStyleName = "";
    try
    {
      paraStyleName =
        AnyConverter.toString(UNO.getProperty(paragraph, "ParaStyleName"));
    }
    catch (IllegalArgumentException e)
    {}
    return paraStyleName.startsWith(ParaStyleNameVerfuegungspunkt);
  }

  /**
   * Liefert true, wenn es sich bei dem �bergebenen Absatz paragraph um einen als
   * VerfuegungspunktMitZuleitung markierten Absatz handelt.
   * 
   * @param paragraph
   *          Das Objekt mit der Property ParaStyleName, die f�r den Vergleich
   *          herangezogen wird.
   * @return true, wenn der Name des Absatzformates mit
   *         "WollMuxVerfuegungspunktMitZuleitung" beginnt.
   */
  private static boolean isVerfuegungspunktMitZuleitung(XTextRange paragraph)
  {
    String paraStyleName = "";
    try
    {
      paraStyleName =
        AnyConverter.toString(UNO.getProperty(paragraph, "ParaStyleName"));
    }
    catch (IllegalArgumentException e)
    {}
    return paraStyleName.startsWith(ParaStyleNameVerfuegungspunktMitZuleitung);
  }

  /**
   * Liefert true, wenn es sich bei dem �bergebenen Absatz paragraph um einen als
   * Zuleitungszeile markierten Absatz handelt.
   * 
   * @param paragraph
   *          Das Objekt mit der Property ParaStyleName, die f�r den Vergleich
   *          herangezogen wird.
   * @return true, wenn der Name des Absatzformates mit "WollMuxZuleitungszeile"
   *         beginnt.
   */
  private static boolean isZuleitungszeile(XTextRange paragraph)
  {
    String paraStyleName = "";
    try
    {
      paraStyleName =
        AnyConverter.toString(UNO.getProperty(paragraph, "ParaStyleName"));
    }
    catch (IllegalArgumentException e)
    {}
    return paraStyleName.startsWith(ParaStyleNameZuleitungszeile);
  }

  /**
   * Liefert true, wenn der �bergebene Paragraph paragraph den f�r Abdrucke typischen
   * String in der Form "Abdruck von I[, II, ...][ und n]" enth�lt, andernfalls
   * false.
   * 
   * @param paragraph
   *          der zu pr�fende Paragraph
   * @return
   */
  private static boolean isAbdruck(XTextRange paragraph)
  {
    String str = paragraph.getString();
    return str.contains(L.m("Abdruck von I."))
      || str.contains(L.m("Abdruck von <Vorg�nger>."));
  }

  /**
   * Liefert den letzten Teil suffix, der am Ende eines Abdruck-Strings der Form
   * "Abdruck von I[, II, ...][ und n]<suffix>" gefunden wird oder "", wenn der kein
   * Teil gefunden wurde. Das entspricht dem Text, den der Benutzer manuell
   * hinzugef�gt hat.
   * 
   * @param paragraph
   *          der Paragraph, der den Abdruck-String enth�lt.
   * @return den suffix des Abdruck-Strings, der �berlicherweise vom Benutzer manuell
   *         hinzugef�gt wurde.
   */
  private static String getAbdruckSuffix(XTextRange paragraph)
  {
    String str = paragraph.getString();
    Matcher m =
      Pattern.compile(
        "[XIV0-9]+\\.\\s*Abdruck von I\\.(, [XIV0-9]+\\.)*( und [XIV0-9]+\\.)?(.*)").matcher(
        str);
    if (m.matches()) return m.group(3);
    return "";
  }

  /**
   * Z�hlt die Anzahl Verf�gungspunkte im Dokument vor der Position von
   * range.getStart() (einschlie�lich) und liefert deren Anzahl zur�ck, wobei auch
   * ein evtl. vorhandener Rahmen WollMuxVerfuegungspunkt1 mit gez�hlt wird.
   * 
   * @param doc
   *          Das Dokument in dem sich range befindet (wird ben�tigt f�r den Rahmen
   *          WollMuxVerfuegungspunkt1)
   * @param range
   *          Die TextRange, bei der mit der Z�hlung begonnen werden soll.
   * @return die Anzahl Verf�gungspunkte vor und mit range.getStart()
   */
  public static int countVerfPunkteBefore(XTextDocument doc, XParagraphCursor range)
  {
    int count = 0;

    // Z�hler f�r Verfuegungspunktnummer auf 1 initialisieren, wenn ein
    // Verfuegungspunkt1 vorhanden ist.
    XTextRange punkt1 = getVerfuegungspunkt1(doc);
    if (punkt1 != null) count++;

    XParagraphCursor cursor =
      UNO.XParagraphCursor(range.getText().createTextCursorByRange(range.getStart()));
    if (cursor != null) do
    {
      if (isVerfuegungspunkt(cursor)) count++;
    } while (cursor.gotoPreviousParagraph(false));

    return count;
  }

  /**
   * Sucht nach allen Abs�tzen im Haupttextbereich des Dokuments doc (also nicht in
   * Frames), deren Absatzformatname mit "WollMuxVerfuegungspunkt" beginnt und
   * numeriert die bereits vorhandenen r�mischen Ziffern neu durch oder erzeugt eine
   * neue Ziffer, wenn in einem entsprechenden Verf�gungspunkt noch keine Ziffer
   * gesetzt wurde. Ist ein Rahmen mit dem Namen WollMuxVerfuegungspunkt1 vorhanden,
   * der einen als Verf�gungpunkt markierten Paragraphen enth�lt, so wird dieser
   * Paragraph immer (gem�� Konzept) als Verf�gungspunkt "I" behandelt.
   * 
   * @param doc
   *          Das Dokument, in dem alle Verf�gungspunkte angepasst werden sollen.
   */
  public static void ziffernAnpassen(TextDocumentModel model)
  {
    XTextRange punkt1 = getVerfuegungspunkt1(model.doc);

    // Z�hler f�r Verfuegungspunktnummer auf 1 initialisieren, wenn ein
    // Verfuegungspunkt1 vorhanden ist.
    int count = 0;
    if (punkt1 != null) count++;

    // Paragraphen des Texts enumerieren und dabei alle Verfuegungspunkte neu
    // nummerieren. Die Enumeration erfolgt �ber einen ParagraphCursor, da sich
    // dieser stabiler verh�lt als das Durchgehen der XEnumerationAccess, bei
    // der es zu OOo-Abst�rzen kam. Leider konnte ich das Problem nicht exakt
    // genug isolieren um ein entsprechende Ticket bei OOo dazu aufmachen zu
    // k�nnen, da der Absturz nur sporadisch auftrat.
    XParagraphCursor cursor =
      UNO.XParagraphCursor(model.doc.getText().createTextCursorByRange(
        model.doc.getText().getStart()));
    if (cursor != null) do
    {
      // ganzen Paragraphen markieren
      cursor.gotoEndOfParagraph(true);

      if (isVerfuegungspunkt(cursor))
      {
        count++;

        if (isAbdruck(cursor))
        {
          // Behandlung von Paragraphen mit einem "Abdruck"-String
          String abdruckStr = abdruckString(count) + getAbdruckSuffix(cursor);
          if (!cursor.getString().equals(abdruckStr))
          {
            cursor.setString(abdruckStr);
            formatRoemischeZifferOnly(cursor);
          }
        }
        else
        {
          // Behandlung von normalen Verf�gungspunkten:
          String numberStr = romanNumber(count) + "\t";
          XTextRange zifferOnly = getZifferOnly(cursor, false);
          if (zifferOnly != null)
          {
            // Nummer aktualisieren wenn sie nicht mehr stimmt.
            if (!zifferOnly.getString().equals(numberStr))
              zifferOnly.setString(numberStr);
          }
          else
          {
            // Nummer neu anlegen, wenn wie noch gar nicht existierte
            zifferOnly = cursor.getText().createTextCursorByRange(cursor.getStart());
            zifferOnly.setString(numberStr);
            formatRoemischeZifferOnly(zifferOnly);
          }
        }
      }
    } while (cursor.gotoNextParagraph(false));

    // Verfuegungspunt1 setzen
    if (punkt1 != null)
    {
      XTextRange zifferOnly = getZifferOnly(punkt1, false);
      if (zifferOnly != null)
      {
        if (count == 1) zifferOnly.setString("");
      }
      else
      {
        if (count > 1) punkt1.getStart().setString(romanNumbers[0]);
      }
    }

    // Setzte die Druckfunktion SachleitendeVerfuegung wenn mindestens manuell
    // eingef�gter Verf�gungspunkt vorhanden ist. Ansonsten setze die
    // Druckfunktion zur�ck.
    int effectiveCount = (punkt1 != null) ? count - 1 : count;
    if (effectiveCount > 0)
      model.addPrintFunction(PRINT_FUNCTION_NAME);
    else
      model.removePrintFunction(PRINT_FUNCTION_NAME);
  }

  /**
   * Liefert eine XTextRange, die genau die r�mische Ziffer (falls vorhanden mit
   * darauf folgendem \t-Zeichen) am Beginn eines Absatzes umschlie�t oder null,
   * falls keine Ziffer gefunden wurde. Bei der Suche nach der Ziffer werden nur die
   * ersten 7 Zeichen des Absatzes gepr�ft.
   * 
   * @param par
   *          die TextRange, die den Paragraphen umschlie�t, in dessen Anfang nach
   *          der r�mischen Ziffer gesucht werden soll.
   * @param includeNoTab
   *          ist includeNoTab == true, so enth�lt der cursor immer nur die Ziffer
   *          ohne das darauf folgende Tab-Zeichen.
   * @return die TextRange, die genau die r�mische Ziffer umschlie�t falls eine
   *         gefunden wurde oder null, falls keine Ziffer gefunden wurde.
   */
  private static XTextCursor getZifferOnly(XTextRange par, boolean includeNoTab)
  {
    XParagraphCursor cursor =
      UNO.XParagraphCursor(par.getText().createTextCursorByRange(par.getStart()));

    for (int i = 0; i < 7; i++)
    {
      String text = "";
      if (!cursor.isEndOfParagraph())
      {
        cursor.goRight((short) 1, true);
        text = cursor.getString();
        if (includeNoTab) text += "\t";
      }
      else
      {
        // auch eine Ziffer erkennen, die nicht mit \t endet.
        text = cursor.getString() + "\t";
      }
      if (text.matches(zifferPattern + "$")) return cursor;
    }

    return null;
  }

  /**
   * Liefert das Textobjekt des TextRahmens WollMuxVerfuegungspunkt1 oder null, falls
   * der Textrahmen nicht existiert. Der gesamte Text innerhalb des Textrahmens wird
   * dabei automatisch mit dem Absatzformat WollMuxVerfuegungspunkt1 vordefiniert.
   * 
   * @param doc
   *          das Dokument, in dem sich der TextRahmen WollMuxVerfuegungspunkt1
   *          befindet (oder nicht).
   * @return Das Textobjekts des TextRahmens WollMuxVerfuegungspunkt1 oder null,
   *         falls der Textrahmen nicht existiert.
   */
  private static XTextRange getVerfuegungspunkt1(XTextDocument doc)
  {
    XTextFrame frame = null;
    try
    {
      frame =
        UNO.XTextFrame(UNO.XTextFramesSupplier(doc).getTextFrames().getByName(
          FrameNameVerfuegungspunkt1));
    }
    catch (java.lang.Exception e)
    {}

    if (frame != null)
    {
      XTextCursor cursor = frame.getText().createTextCursorByRange(frame.getText());
      if (isVerfuegungspunkt(cursor)) return cursor;

      // Absatzformat WollMuxVerfuegungspunkt1 setzen wenn noch nicht gesetzt.
      UNO.setProperty(cursor, "ParaStyleName", ParaStyleNameVerfuegungspunkt1);
      return cursor;
    }
    else
      return null;
  }

  /**
   * Erzeugt einen String in der Form "i.<tab>Abdruck von I.[, II., ...][ und
   * <i-1>]", der passend zu einem Abdruck mit der Verf�gungsnummer number angezeigt
   * werden soll.
   * 
   * @param number
   *          Die Nummer des Verf�gungspunktes des Abdrucks
   * @return String in der Form "Abdruck von I.[, II., ...][ und <i-1>]" oder
   *         AbdruckDefaultStr, wenn der Verf�gungspunkt bei i==0 und i==1 keinen
   *         Vorg�nger besitzen kann.
   */
  private static String abdruckString(int number)
  {
    String str = romanNumber(number) + "\t" + L.m("Abdruck von ") + romanNumber(1);
    for (int j = 2; j < (number - 1); ++j)
      str += ", " + romanNumber(j);
    if (number >= 3) str += L.m(" und ") + romanNumber(number - 1);
    return str;
  }

  /**
   * Liefert die r�mische Zahl zum �bgebenen integer Wert i. Die r�mischen Zahlen
   * werden dabei aus dem begrenzten Array romanNumbers ausgelesen. Ist i kein
   * g�ltiger Index des Arrays, so sieht der R�ckgabewert wie folgt aus "<dezimalzahl(i)>.".
   * Hier kann bei Notwendigkeit nat�rlich auch ein Berechnungsschema f�r r�mische
   * Zahlen implementiert werden, was f�r die Sachleitenden Verf�gungen vermutlich
   * aber nicht erforderlich sein wird.
   * 
   * @param i
   *          Die Zahl, zu der eine r�mische Zahl geliefert werden soll.
   * @return Die r�mische Zahl, oder "<dezimalzahl(i)>, wenn i nicht in den
   *         Arraygrenzen von romanNumbers.
   */
  private static String romanNumber(int i)
  {
    String number = "" + i + ".";
    if (i > 0 && i <= romanNumbers.length) number = romanNumbers[i - 1];
    return number;
  }

  /**
   * Erzeugt einen Vector mit Elementen vom Typ Verfuegungspunkt, der dem Druckdialog
   * �bergeben werden kann und alle f�r den Druckdialog notwendigen Informationen
   * enth�lt.
   * 
   * @param doc
   *          Das zu scannende Dokument
   * @return Vector of Verfuegungspunkt, der f�r jeden Verfuegungspunkt im Dokument
   *         doc einen Eintrag enth�lt.
   */
  private static Vector<Verfuegungspunkt> scanVerfuegungspunkte(XTextDocument doc)
  {
    Vector<Verfuegungspunkt> verfuegungspunkte = new Vector<Verfuegungspunkt>();

    // Verf�gungspunkt1 hinzuf�gen wenn verf�gbar.
    XTextRange punkt1 = getVerfuegungspunkt1(doc);
    if (punkt1 != null)
    {
      Verfuegungspunkt original = new Verfuegungspunkt(L.m("I. Original"));
      original.addZuleitungszeile(L.m("Empf�nger siehe Empf�ngerfeld"));
      verfuegungspunkte.add(original);
    }

    Verfuegungspunkt currentVerfpunkt = null;

    // Paragraphen des Texts enumerieren und Verf�gungspunkte erstellen. Die
    // Enumeration erfolgt �ber einen ParagraphCursor, da sich
    // dieser stabiler verh�lt als das Durchgehen der XEnumerationAccess, bei
    // der es zu OOo-Abst�rzen kam. Leider konnte ich das Problem nicht exakt
    // genug isolieren um ein entsprechende Ticket bei OOo dazu aufmachen zu
    // k�nnen, da der Absturz nur sporadisch auftrat.
    XParagraphCursor cursor =
      UNO.XParagraphCursor(doc.getText().createTextCursorByRange(
        doc.getText().getStart()));

    if (cursor != null) do
    {
      // ganzen Paragraphen markieren
      cursor.gotoEndOfParagraph(true);

      if (isVerfuegungspunkt(cursor))
      {
        String heading = cursor.getString();
        currentVerfpunkt = new Verfuegungspunkt(heading);
        currentVerfpunkt.setMinNumberOfCopies(1);
        verfuegungspunkte.add(currentVerfpunkt);
      }

      // Zuleitungszeilen hinzuf�gen (auch wenn der Paragraph Verf�gungspunkt
      // und Zuleitungszeile zugleich ist)
      if ((isZuleitungszeile(cursor) || isVerfuegungspunktMitZuleitung(cursor))
        && currentVerfpunkt != null)
      {
        String zuleit = cursor.getString();
        // nicht leere Zuleitungszeilen zum Verf�gungspunkt hinzuf�gen.
        if (!(zuleit.length() == 0)) currentVerfpunkt.addZuleitungszeile(zuleit);
      }
    } while (cursor.gotoNextParagraph(false));

    return verfuegungspunkte;
  }

  /**
   * Repr�sentiert einen vollst�ndigen Verf�gungspunkt, der aus �berschrift (r�mische
   * Ziffer + �berschrift) und Inhalt besteht. Die Klasse bietet Methden an, �ber die
   * auf alle f�r den Druck wichtigen Eigenschaften des Verf�gungspunktes zugegriffen
   * werden kann (z.B. �berschrift, Anzahl Zuleitungszeilen, ...)
   * 
   * @author christoph.lutz
   */
  public static class Verfuegungspunkt
  {
    /**
     * Enth�lt den vollst�ndigen Text der erste Zeile des Verf�gungspunktes
     * einschlie�lich der r�mischen Ziffer.
     */
    protected String heading;

    /**
     * Vector of String, der alle Zuleitungszeilen enth�lt, die mit addParagraph
     * hinzugef�gt wurden.
     */
    protected Vector<String> zuleitungszeilen;

    /**
     * Enth�lt die Anzahl der Ausdrucke, die mindestens ausgedruckt werden sollen.
     */
    protected int minNumberOfCopies;

    /**
     * Erzeugt einen neuen Verf�gungspunkt, wobei firstPar der Absatz ist, der die
     * erste Zeile mit der r�mischen Ziffer und der �berschrift enth�lt.
     * 
     * @param heading
     *          Text der ersten Zeile des Verf�gungspunktes mit der r�mischen Ziffer
     *          und der �berschrift.
     */
    public Verfuegungspunkt(String heading)
    {
      this.heading = heading;
      this.zuleitungszeilen = new Vector<String>();
      this.minNumberOfCopies = 0;
    }

    /**
     * F�gt einen weitere Zuleitungszeile des Verf�gungspunktes hinzu (wenn paragraph
     * nicht null ist).
     * 
     * @param paragraph
     *          XTextRange, das den gesamten Paragraphen der Zuleitungszeile enth�lt.
     */
    public void addZuleitungszeile(String zuleitung)
    {
      zuleitungszeilen.add(zuleitung);
    }

    /**
     * Liefert die Anzahl der Ausfertigungen zur�ck, mit denen der Verf�gungspunkt
     * ausgeduckt werden soll; Die Anzahl erh�ht sich mit jeder hinzugef�gten
     * Zuleitungszeile. Der Mindestwert kann mit setMinNumberOfCopies gesetzt werden.
     * 
     * @return Anzahl der Ausfertigungen mit denen der Verf�gungspunkt gedruckt
     *         werden soll.
     */
    public int getNumberOfCopies()
    {
      if (zuleitungszeilen.size() > minNumberOfCopies)
        return zuleitungszeilen.size();
      else
        return minNumberOfCopies;
    }

    /**
     * Setzt die Anzahl der Ausfertigungen, die Mindestens ausgedruckt werden sollen,
     * auch dann wenn z.B. keine Zuleitungszeilen vorhanden sind.
     * 
     * @param minNumberOfCopies
     *          Anzahl der Ausfertigungen mit denen der Verf�gungspunkt mindestens
     *          ausgedruckt werden soll.
     */
    public void setMinNumberOfCopies(int minNumberOfCopies)
    {
      this.minNumberOfCopies = minNumberOfCopies;
    }

    /**
     * Liefert einen Vector of Strings, der die Texte der Zuleitungszeilen
     * beinhaltet, die dem Verf�gungspunkt mit addParagraph hinzugef�gt wurden.
     * 
     * @return Vector of Strings mit den Texten der Zuleitungszeilen.
     */
    public Vector<String> getZuleitungszeilen()
    {
      return zuleitungszeilen;
    }

    /**
     * Liefert den Text der �berschrift des Verf�gungspunktes einschlie�lich der
     * r�mischen Ziffer als String zur�ck, wobei mehrfache Leerzeichen (\s+) durch
     * einfache Leerzeichen ersetzt werden.
     * 
     * @return r�mischer Ziffer + �berschrift
     */
    public String getHeading()
    {
      String text = heading;

      // Tabs und Spaces durch single spaces ersetzen
      text = text.replaceAll("\\s+", " ");

      return text;
    }
  }

  /**
   * Zeigt den Druckdialog f�r Sachleitende Verf�gungen an und liefert die dort
   * getroffenen Einstellungen als Liste von VerfuegungspunktInfo-Objekten zur�ck,
   * oder null, wenn Fehler auftraten oder der Druckvorgang abgebrochen wurde.
   * 
   * @param doc
   *          Das Dokument, aus dem die anzuzeigenden Verf�gungspunkte ausgelesen
   *          werden.
   */
  public static List<VerfuegungspunktInfo> callPrintDialog(XTextDocument doc)
  {
    Vector<Verfuegungspunkt> vps = scanVerfuegungspunkte(doc);
    Iterator<Verfuegungspunkt> iter = vps.iterator();
    while (iter.hasNext())
    {
      Verfuegungspunkt vp = iter.next();
      String text = L.m("Verf�gungspunkt '%1'", vp.getHeading());
      Iterator<String> zuleits = vp.getZuleitungszeilen().iterator();
      while (zuleits.hasNext())
      {
        String zuleit = zuleits.next();
        text += "\n  --> '" + zuleit + "'";
      }
      Logger.debug2(text);
    }

    // Beschreibung des Druckdialogs auslesen.
    ConfigThingy conf = WollMuxSingleton.getInstance().getWollmuxConf();
    ConfigThingy svdds =
      conf.query("Dialoge").query("SachleitendeVerfuegungenDruckdialog");
    ConfigThingy printDialogConf = null;
    try
    {
      printDialogConf = svdds.getLastChild();
    }
    catch (NodeNotFoundException e)
    {
      Logger.error(
        L.m("Fehlende Dialogbeschreibung f�r den Dialog 'SachleitendeVerfuegungenDruckdialog'."),
        e);
      return null;
    }

    // Dialog ausf�hren und R�ckgabewert zur�ckliefern.
    try
    {
      final ActionEvent[] result = new ActionEvent[] { null };
      new SachleitendeVerfuegungenDruckdialog(printDialogConf, vps,
        new ActionListener()
        {
          public void actionPerformed(ActionEvent e)
          {
            synchronized (result)
            {
              result[0] = e;
              result.notifyAll();
            }
          }
        });
      synchronized (result)
      {
        while (result[0] == null)
          try
          {
            result.wait();
          }
          catch (InterruptedException e1)
          {
            Logger.error(e1);
            return null;
          }
      }
      String cmd = result[0].getActionCommand();
      SachleitendeVerfuegungenDruckdialog slvd =
        (SachleitendeVerfuegungenDruckdialog) result[0].getSource();
      if (SachleitendeVerfuegungenDruckdialog.CMD_SUBMIT.equals(cmd) && slvd != null)
      {
        return slvd.getCurrentSettings();
      }
      return null;
    }
    catch (ConfigurationErrorException e)
    {
      Logger.error(e);
      return null;
    }
  }

  /**
   * Liefert die Anzahl der im XTextDocument doc enthaltenen Verf�gungspunkte zur�ck.
   * 
   * @param doc
   *          das TextDocument in dem gez�hlt werden soll.
   * @return die Anzahl der im XTextDocument doc enthaltenen Verf�gungspunkte
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  public static int countVerfuegungspunkte(XTextDocument doc)
  {
    if (doc != null)
      return scanVerfuegungspunkte(doc).size();
    else
      return 0;
  }

  /**
   * Druckt den Verf�gungpunkt Nummer verfPunkt aus dem Dokument aus, das im
   * XPrintModel pmod hinterlegt ist.
   * 
   * @param pmod
   *          Das PrintModel zu diesem Druckvorgang.
   * @param verfPunkt
   *          Die Nummer des auszuduruckenden Verf�gungspunktes, wobei alle folgenden
   *          Verf�gungspunkte ausgeblendet werden.
   * @param isDraft
   *          wenn isDraft==true, werden alle draftOnly-Bl�cke eingeblendet,
   *          ansonsten werden sie ausgeblendet.
   * @param isOriginal
   *          wenn isOriginal, wird die Ziffer des Verf�gungspunktes I ausgeblendet
   *          und alle notInOriginal-Bl�cke ebenso. Andernfalls sind Ziffer und
   *          notInOriginal-Bl�cke eingeblendet.
   * @param copyCount
   *          enth�lt die Anzahl der Kopien, die von diesem Verf�gungspunkt erstellt
   *          werden sollen.
   * @throws PrintFailedException
   */
  public static void printVerfuegungspunkt(XPrintModel pmod, int verfPunkt,
      boolean isDraft, boolean isOriginal, short copyCount)
  {
    XTextDocument doc = pmod.getTextDocument();

    // Steht der viewCursor in einem Bereich, der im folgenden ausgeblendet
    // wird, dann wird der ViewCursor in einen sichtbaren Bereich verschoben. Um
    // den viewCursor wieder herstellen zu k�nnen, wird er hier gesichert und
    // sp�ter wieder hergestellt.
    XTextCursor vc = null;
    XTextCursor oldViewCursor = null;
    XTextViewCursorSupplier suppl =
      UNO.XTextViewCursorSupplier(UNO.XModel(pmod.getTextDocument()).getCurrentController());
    if (suppl != null) vc = suppl.getViewCursor();
    if (vc != null) oldViewCursor = vc.getText().createTextCursorByRange(vc);

    // Z�hler f�r Verfuegungspunktnummer auf 1 initialisieren, wenn ein
    // Verfuegungspunkt1 vorhanden ist.
    XTextRange punkt1 = getVerfuegungspunkt1(doc);
    int count = 0;
    if (punkt1 != null) count++;

    // Auszublendenden Bereich festlegen:
    XTextRange setInvisibleRange = null;
    XParagraphCursor cursor =
      UNO.XParagraphCursor(doc.getText().createTextCursorByRange(
        doc.getText().getStart()));
    if (cursor != null) do
    {
      cursor.gotoEndOfParagraph(true);

      if (isVerfuegungspunkt(cursor))
      {
        // Punkt1 merken
      if (punkt1 == null) punkt1 = cursor.getText().createTextCursorByRange(cursor);

      count++;

      if (count == (verfPunkt + 1))
      {
        cursor.collapseToStart();
        cursor.gotoRange(cursor.getText().getEnd(), true);
        setInvisibleRange = cursor;
      }
    }
  } while (setInvisibleRange == null && cursor.gotoNextParagraph(false));

    // Pr�fen, welche Textsections im ausgeblendeten Bereich liegen und diese
    // ebenfalls ausblenden:
    List<XTextSection> hidingSections =
      getSectionsFromPosition(pmod.getTextDocument(), setInvisibleRange);
    for (Iterator<XTextSection> iter = hidingSections.iterator(); iter.hasNext();)
    {
      UNO.setProperty(iter.next(), "IsVisible", Boolean.FALSE);
    }

    // ensprechende Verf�gungspunkte ausblenden
    if (setInvisibleRange != null) UNO.hideTextRange(setInvisibleRange, true);

    // Ein/Ausblenden Druckbl�cke (z.B. draftOnly):
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_DRAFT_ONLY, isDraft, false);
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_NOT_IN_ORIGINAL, !isOriginal, false);
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_ORIGINAL_ONLY, isOriginal, false);
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_ALL_VERSIONS, true, false);

    // Ein/Ausblenden der Sichtbarkeitsgruppen:
    pmod.setGroupVisible(GROUP_ID_SLV_DRAFT_ONLY, isDraft);
    pmod.setGroupVisible(GROUP_ID_SLV_NOT_IN_ORIGINAL, !isOriginal);
    pmod.setGroupVisible(GROUP_ID_SLV_ORIGINAL_ONLY, isOriginal);
    pmod.setGroupVisible(GROUP_ID_SLV_ALL_VERSIONS, true);

    // Ziffer von Punkt 1 ausblenden falls isOriginal
    XTextRange punkt1ZifferOnly = null;
    if (isOriginal && punkt1 != null)
    {
      punkt1ZifferOnly = getZifferOnly(punkt1, true);
      UNO.hideTextRange(punkt1ZifferOnly, true);
    }

    // -----------------------------------------------------------------------
    // Druck des Dokuments
    // -----------------------------------------------------------------------
    for (int j = 0; j < copyCount; ++j)
      pmod.printWithProps();

    // Ausblendung von Ziffer von Punkt 1 wieder aufheben
    if (punkt1ZifferOnly != null) UNO.hideTextRange(punkt1ZifferOnly, false);

    // Sichtbarkeitsgruppen wieder einblenden
    pmod.setGroupVisible(GROUP_ID_SLV_DRAFT_ONLY, true);
    pmod.setGroupVisible(GROUP_ID_SLV_NOT_IN_ORIGINAL, true);
    pmod.setGroupVisible(GROUP_ID_SLV_ORIGINAL_ONLY, true);
    pmod.setGroupVisible(GROUP_ID_SLV_ALL_VERSIONS, true);

    // Alte Eigenschaften der Druckbl�cke wieder herstellen:
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_DRAFT_ONLY, true, true);
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_NOT_IN_ORIGINAL, true, true);
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_ORIGINAL_ONLY, true, true);
    pmod.setPrintBlocksProps(BLOCKNAME_SLV_ALL_VERSIONS, true, true);

    // ausgeblendete TextSections wieder einblenden
    for (Iterator<XTextSection> iter = hidingSections.iterator(); iter.hasNext();)
    {
      UNO.setProperty(iter.next(), "IsVisible", Boolean.TRUE);
    }

    // Verf�gungspunkte wieder einblenden:
    if (setInvisibleRange != null) UNO.hideTextRange(setInvisibleRange, false);

    // ViewCursor wieder herstellen:
    if (vc != null && oldViewCursor != null) vc.gotoRange(oldViewCursor, false);
  }

  /**
   * Workaround f�r OOo-Issue 103137, der das selbe macht, wie
   * {@link #printVerfuegungspunkt(XPrintModel, int, boolean, boolean, short)} nach
   * Beendigung des Drucks eines Originals - es setzt alle Verf�gungspunkte,
   * Druckbl�cke und Sichtbarkeitsgruppen aus model auf sichtbar.
   */
  public static void workaround103137(TextDocumentModel model)
  {
    if (model == null || model.doc == null) return;
    XTextDocument doc = model.doc;
    XParagraphCursor cursor =
      UNO.XParagraphCursor(doc.getText().createTextCursorByRange(
        doc.getText().getStart()));
    if (cursor == null) return;

    // Punkt1 und den wieder Einzublendenden Bereich festlegen:
    XTextRange punkt1 = getVerfuegungspunkt1(doc);
    XTextRange setInvisibleRange = null;
    do
    {
      cursor.gotoEndOfParagraph(true);

      if (isVerfuegungspunkt(cursor))
      {
        if (punkt1 == null)
          punkt1 = cursor.getText().createTextCursorByRange(cursor);
        cursor.collapseToStart();
        cursor.gotoRange(cursor.getText().getEnd(), true);
        setInvisibleRange = cursor;
      }
    } while (setInvisibleRange == null && cursor.gotoNextParagraph(false));

    // Sammeln der Textsections, die im ausgeblendeten Bereich liegen
    List<XTextSection> hidingSections =
      getSectionsFromPosition(doc, setInvisibleRange);

    // Ausblendung der Ziffer von Punkt 1 aufheben:
    if (punkt1 != null)
    {
      XTextRange punkt1ZifferOnly = getZifferOnly(punkt1, true);
      if (punkt1ZifferOnly != null) UNO.hideTextRange(punkt1ZifferOnly, false);
    }

    // Sichtbarkeitsgruppen einblenden:
    WollMuxEventHandler.handleSetVisibleState(model, GROUP_ID_SLV_DRAFT_ONLY, true,
      null);
    WollMuxEventHandler.handleSetVisibleState(model, GROUP_ID_SLV_NOT_IN_ORIGINAL,
      true, null);
    WollMuxEventHandler.handleSetVisibleState(model, GROUP_ID_SLV_ORIGINAL_ONLY,
      true, null);
    WollMuxEventHandler.handleSetVisibleState(model, GROUP_ID_SLV_ALL_VERSIONS,
      true, null);

    // Druckbl�cke wieder einblenden:
    model.setPrintBlocksProps(BLOCKNAME_SLV_DRAFT_ONLY, true, true);
    model.setPrintBlocksProps(BLOCKNAME_SLV_NOT_IN_ORIGINAL, true, true);
    model.setPrintBlocksProps(BLOCKNAME_SLV_ORIGINAL_ONLY, true, true);
    model.setPrintBlocksProps(BLOCKNAME_SLV_ALL_VERSIONS, true, true);

    // ausgeblendete TextSections wieder einblenden:
    for (XTextSection sect : hidingSections)
      UNO.setProperty(sect, "IsVisible", Boolean.TRUE);

    // Verf�gungspunkte wieder einblenden:
    if (setInvisibleRange != null) UNO.hideTextRange(setInvisibleRange, false);
  }

  /**
   * Diese Methode liefert in eine Liste aller Textsections aus doc, deren Anker an
   * der selben Position oder hinter der Position der TextRange pos liegt.
   * 
   * @param doc
   *          Textdokument in dem alle enthaltenen Textsections gepr�ft werden.
   * @param pos
   *          Position, ab der die TextSections in den Vector aufgenommen werden
   *          sollen.
   * @return eine Liste aller TextSections, die an oder nach pos starten oder eine
   *         leere Liste, wenn es Fehler gab oder keine Textsection gefunden wurde.
   * 
   * @author Christoph Lutz (D-III-ITD-5.1)
   */
  private static List<XTextSection> getSectionsFromPosition(XTextDocument doc,
      XTextRange pos)
  {
    Vector<XTextSection> v = new Vector<XTextSection>();
    if (pos == null) return v;
    XTextRangeCompare comp = UNO.XTextRangeCompare(pos.getText());
    if (comp == null) return v;
    XTextSectionsSupplier suppl = UNO.XTextSectionsSupplier(doc);
    if (suppl == null) return v;

    XNameAccess sections = suppl.getTextSections();
    String[] names = sections.getElementNames();
    for (int i = 0; i < names.length; i++)
    {
      XTextSection section = null;
      try
      {
        section = UNO.XTextSection(sections.getByName(names[i]));
      }
      catch (java.lang.Exception e)
      {
        Logger.error(e);
      }

      if (section != null)
      {
        try
        {
          int diff = comp.compareRegionStarts(pos, section.getAnchor());
          if (diff >= 0) v.add(section);
        }
        catch (IllegalArgumentException e)
        {
          // kein Fehler, da die Exceptions immer fliegt, wenn die ranges in
          // unterschiedlichen Textobjekten liegen.
        }
      }
    }
    return v;
  }

  /**
   * Liefert das Absatzformat (=ParagraphStyle) des Dokuments doc mit dem Namen name
   * oder null, falls das Absatzformat nicht definiert ist.
   * 
   * @param doc
   *          das Dokument in dem nach einem Absatzformat name gesucht werden soll.
   * @param name
   *          der Name des gesuchten Absatzformates
   * @return das Absatzformat des Dokuments doc mit dem Namen name oder null, falls
   *         das Absatzformat nicht definiert ist.
   */
  private static XStyle getParagraphStyle(XTextDocument doc, String name)
  {
    XStyle style = null;

    XNameContainer pss = getStyleContainer(doc, PARAGRAPH_STYLES);
    if (pss != null) try
    {
      style = UNO.XStyle(pss.getByName(name));
    }
    catch (java.lang.Exception e)
    {}
    return style;
  }

  /**
   * Erzeugt im Dokument doc ein neues Absatzformat (=ParagraphStyle) mit dem Namen
   * name und dem ParentStyle parentStyleName und liefert das neu erzeugte
   * Absatzformat zur�ck oder null, falls das Erzeugen nicht funktionierte.
   * 
   * @param doc
   *          das Dokument in dem das Absatzformat name erzeugt werden soll.
   * @param name
   *          der Name des zu erzeugenden Absatzformates
   * @param parentStyleName
   *          Name des Vorg�nger-Styles von dem die Eigenschaften dieses Styles
   *          abgeleitet werden soll oder null, wenn kein Vorg�nger gesetzt werden
   *          soll (in diesem Fall wird automatisch "Standard" verwendet)
   * @return das neu erzeugte Absatzformat oder null, falls das Absatzformat nicht
   *         erzeugt werden konnte.
   */
  private static XStyle createParagraphStyle(XTextDocument doc, String name,
      String parentStyleName)
  {
    XNameContainer pss = getStyleContainer(doc, PARAGRAPH_STYLES);
    XStyle style = null;
    try
    {
      style =
        UNO.XStyle(UNO.XMultiServiceFactory(doc).createInstance(
          "com.sun.star.style.ParagraphStyle"));
      pss.insertByName(name, style);
      if (style != null && parentStyleName != null)
        style.setParentStyle(parentStyleName);
      return UNO.XStyle(pss.getByName(name));
    }
    catch (Exception e)
    {}
    return null;
  }

  /**
   * Liefert das Zeichenformat (=CharacterStyle) des Dokuments doc mit dem Namen name
   * oder null, falls das Format nicht definiert ist.
   * 
   * @param doc
   *          das Dokument in dem nach einem Zeichenformat name gesucht werden soll.
   * @param name
   *          der Name des gesuchten Zeichenformates
   * @return das Zeichenformat des Dokuments doc mit dem Namen name oder null, falls
   *         das Absatzformat nicht definiert ist.
   */
  private static XStyle getCharacterStyle(XTextDocument doc, String name)
  {
    XStyle style = null;

    XNameContainer pss = getStyleContainer(doc, CHARACTER_STYLES);
    if (pss != null) try
    {
      style = UNO.XStyle(pss.getByName(name));
    }
    catch (java.lang.Exception e)
    {}
    return style;
  }

  /**
   * Erzeugt im Dokument doc ein neues Zeichenformat (=CharacterStyle) mit dem Namen
   * name und dem ParentStyle parentStyleName und liefert das neu erzeugte
   * Zeichenformat zur�ck oder null, falls das Erzeugen nicht funktionierte.
   * 
   * @param doc
   *          das Dokument in dem das Zeichenformat name erzeugt werden soll.
   * @param name
   *          der Name des zu erzeugenden Zeichenformates
   * @param parentStyleName
   *          Name des Vorg�nger-Styles von dem die Eigenschaften dieses Styles
   *          abgeleitet werden soll oder null, wenn kein Vorg�nger gesetzt werden
   *          soll (in diesem Fall wird automatisch "Standard" verwendet)
   * @return das neu erzeugte Zeichenformat oder null, falls das Zeichenformat nicht
   *         erzeugt werden konnte.
   */
  private static XStyle createCharacterStyle(XTextDocument doc, String name,
      String parentStyleName)
  {
    XNameContainer pss = getStyleContainer(doc, CHARACTER_STYLES);
    XStyle style = null;
    try
    {
      style =
        UNO.XStyle(UNO.XMultiServiceFactory(doc).createInstance(
          "com.sun.star.style.CharacterStyle"));
      pss.insertByName(name, style);
      if (style != null && parentStyleName != null)
        style.setParentStyle(parentStyleName);
      return UNO.XStyle(pss.getByName(name));
    }
    catch (Exception e)
    {}
    return null;
  }

  /**
   * Liefert den Styles vom Typ type des Dokuments doc.
   * 
   * @param doc
   *          Das Dokument, dessen StyleContainer zur�ckgeliefert werden soll.
   * @param type
   *          kann z.B. CHARACTER_STYLE oder PARAGRAPH_STYLE sein.
   * @return Liefert den Container der Styles vom Typ type des Dokuments doc oder
   *         null, falls der Container nicht bestimmt werden konnte.
   */
  private static XNameContainer getStyleContainer(XTextDocument doc,
      String containerName)
  {
    try
    {
      return UNO.XNameContainer(UNO.XNameAccess(
        UNO.XStyleFamiliesSupplier(doc).getStyleFamilies()).getByName(containerName));
    }
    catch (java.lang.Exception e)
    {}
    return null;
  }

  /**
   * Falls die f�r Sachleitenden Verf�gungen notwendigen Absatz- und Zeichenformate
   * nicht bereits existieren, werden sie hier erzeugt und mit fest verdrahteten
   * Werten vorbelegt.
   * 
   * @param doc
   */
  private static void createUsedStyles(XTextDocument doc)
  {
    XStyle style = null;

    // Absatzformate:

    style = getParagraphStyle(doc, ParaStyleNameDefault);
    if (style == null)
    {
      style = createParagraphStyle(doc, ParaStyleNameDefault, null);
      UNO.setProperty(style, "FollowStyle", ParaStyleNameDefault);
      UNO.setProperty(style, "CharHeight", Integer.valueOf(11));
      UNO.setProperty(style, "CharFontName", "Arial");
    }

    style = getParagraphStyle(doc, ParaStyleNameVerfuegungspunkt);
    if (style == null)
    {
      style =
        createParagraphStyle(doc, ParaStyleNameVerfuegungspunkt,
          ParaStyleNameDefault);
      UNO.setProperty(style, "FollowStyle", ParaStyleNameDefault);
      UNO.setProperty(style, "CharWeight", Float.valueOf(FontWeight.BOLD));
      UNO.setProperty(style, "ParaFirstLineIndent", Integer.valueOf(-700));
      UNO.setProperty(style, "ParaTopMargin", Integer.valueOf(460));
    }

    style = getParagraphStyle(doc, ParaStyleNameVerfuegungspunkt1);
    if (style == null)
    {
      style =
        createParagraphStyle(doc, ParaStyleNameVerfuegungspunkt1,
          ParaStyleNameVerfuegungspunkt);
      UNO.setProperty(style, "FollowStyle", ParaStyleNameDefault);
      UNO.setProperty(style, "ParaFirstLineIndent", Integer.valueOf(0));
      UNO.setProperty(style, "ParaTopMargin", Integer.valueOf(0));
    }

    style = getParagraphStyle(doc, ParaStyleNameAbdruck);
    if (style == null)
    {
      style =
        createParagraphStyle(doc, ParaStyleNameAbdruck,
          ParaStyleNameVerfuegungspunkt);
      UNO.setProperty(style, "FollowStyle", ParaStyleNameDefault);
      UNO.setProperty(style, "CharWeight", Integer.valueOf(100));
      UNO.setProperty(style, "ParaFirstLineIndent", Integer.valueOf(-700));
      UNO.setProperty(style, "ParaTopMargin", Integer.valueOf(460));
    }

    style = getParagraphStyle(doc, ParaStyleNameZuleitungszeile);
    if (style == null)
    {
      style =
        createParagraphStyle(doc, ParaStyleNameZuleitungszeile, ParaStyleNameDefault);
      UNO.setProperty(style, "FollowStyle", ParaStyleNameDefault);
      UNO.setProperty(style, "CharUnderline", Integer.valueOf(1));
      UNO.setProperty(style, "CharWeight", Float.valueOf(FontWeight.BOLD));
    }

    style = getParagraphStyle(doc, ParaStyleNameVerfuegungspunktMitZuleitung);
    if (style == null)
    {
      style =
        createParagraphStyle(doc, ParaStyleNameVerfuegungspunktMitZuleitung,
          ParaStyleNameVerfuegungspunkt);
      UNO.setProperty(style, "FollowStyle", ParaStyleNameDefault);
      UNO.setProperty(style, "CharUnderline", Integer.valueOf(1));
    }

    // Zeichenformate:

    style = getCharacterStyle(doc, CharStyleNameDefault);
    if (style == null)
    {
      style = createCharacterStyle(doc, CharStyleNameDefault, null);
      UNO.setProperty(style, "FollowStyle", CharStyleNameDefault);
      UNO.setProperty(style, "CharHeight", Integer.valueOf(11));
      UNO.setProperty(style, "CharFontName", "Arial");
      UNO.setProperty(style, "CharUnderline", Integer.valueOf(0));
    }

    style = getCharacterStyle(doc, CharStyleNameRoemischeZiffer);
    if (style == null)
    {
      style =
        createCharacterStyle(doc, CharStyleNameRoemischeZiffer, CharStyleNameDefault);
      UNO.setProperty(style, "FollowStyle", CharStyleNameDefault);
      UNO.setProperty(style, "CharWeight", Float.valueOf(FontWeight.BOLD));
    }
  }
}
