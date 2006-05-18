/*
 * Dateiname: Bookmark.java
 * Projekt  : WollMux
 * Funktion : Diese Klasse repr�sentiert ein Bookmark in OOo und bietet Methoden
 *            f�r den vereinfachten Zugriff und die Manipulation von Bookmarks an.
 * 
 * Copyright: Landeshauptstadt M�nchen
 *
 * �nderungshistorie:
 * Datum      | Wer | �nderungsgrund
 * -------------------------------------------------------------------
 * 17.05.2006 | LUT | Dokumentation erg�nzt
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * @version 1.0
 * 
 */
package de.muenchen.allg.itd51.wollmux;

import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XComponent;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextRangeCompare;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import de.muenchen.allg.afid.UnoService;

/**
 * Diese Klasse repr�sentiert ein Bookmark in OOo und bietet Methoden f�r den
 * vereinfachten Zugriff und die Manipulation von Bookmarks an.
 * 
 * @author Christoph Lutz (D-III-ITD-5.1)
 */
public class Bookmark
{
  /**
   * Enth�lt den Namen des Bookmarks
   */
  private String name;

  /**
   * Enth�lt den UnoService des Dokuments dem das Bookmark zugeordnet ist.
   */
  private UnoService document;

  /**
   * Der Konstruktor liefert eine Instanz eines bereits im Dokument doc
   * bestehenden Bookmarks mit dem Namen name zur�ck; ist das Bookmark im
   * angebegenen Dokument nicht enthalten, so wird eine NoSuchElementException
   * zur�ckgegeben.
   * 
   * @param name
   *          Der Name des bereits im Dokument vorhandenen Bookmarks.
   * @param doc
   *          Das Dokument, welches Das Bookmark name enth�lt.
   * @throws NoSuchElementException
   *           Das Bookmark name ist im angegebenen Dokument nicht enthalten.
   */
  public Bookmark(String name, XComponent doc) throws NoSuchElementException
  {
    this.document = new UnoService(doc);
    this.name = name;
    UnoService bookmark = getBookmarkService(name, document);
    if (bookmark.xTextContent() == null)
      throw new NoSuchElementException("Bookmark \""
                                       + name
                                       + "\" existiert nicht.");
  }

  /**
   * Vor jedem Zugriff auf den BookmarkService bookmark sollte der Service neu
   * geholt werden, damit auch der Fall behandelt wird, dass das Bookmark
   * inzwischen vom Anwender gel�scht wurde. Ist das Bookmark nicht (mehr) im
   * Dokument vorhanden, so wird ein new UnoService(null) zur�ckgeliefert,
   * welches leichter verarbeitet werden kann.
   * 
   * @param name
   *          Der Name des bereits im Dokument vorhandenen Bookmarks.
   * @param document
   *          Das Dokument, welches Das Bookmark name enth�lt.
   * @return Den UnoService des Bookmarks name im Dokument document.
   */
  private static UnoService getBookmarkService(String name, UnoService document)
  {
    if (document.xBookmarksSupplier() != null)
    {
      try
      {
        return new UnoService(document.xBookmarksSupplier().getBookmarks()
            .getByName(name));
      }
      catch (WrappedTargetException e)
      {
        Logger.error(e);
      }
      catch (NoSuchElementException e)
      {
      }
    }
    return new UnoService(null);
  }

  /**
   * Diese Methode liefert den (aktuellen) Namen des Bookmarks als String
   * zur�ck.
   * 
   * @return liefert den (aktuellen) Namen des Bookmarks als String zur�ck.
   */
  public String getName()
  {
    return name;
  }

  /**
   * Diese Methode liefert eine String-Repr�sentation mit dem Aufbau "Bookmark[<name>]"
   * zur�ck.
   */
  public String toString()
  {
    return "Bookmark[" + getName() + "]";
  }

  /**
   * Diese Methode vergleicht zwei Bookmarks und liefert ihre Relation zur�ck.
   * Folgende R�ckgabewerte sind m�glich: POS_BBAA = B kommt vor A; POS_BB88 = B
   * startet vor A, aber endet gemeinsam mit A; POS_B88B = B enth�lt A, beginnt
   * und endet aber nicht mit A; POS_88AA = B startet mit A und endet vor A;
   * POS_8888 = A und B starten und enden gleich; POS_88BB = A und B starten
   * gleichzeitig, aber A endet vor B; POS_A88A = A enth�lt B, beginnt und endet
   * jedoch nicht mit B; POS_AA88 = A startet vor B, aber endet mit B; POS_AABB =
   * A kommt vor B; Im Fehlerfall (wenn z.B. einer der beiden Bookmarks nicht
   * (mehr) im Dokument vorhanden ist), wird POS_AABB zur�ckgeliefert - es wird
   * also so getan, als k�me B nach A.
   * 
   * @param b
   *          Das Bookmark B, das mit this (Bookmark A) verglichen werden soll.
   * @return Die Relation der beiden Bookmark in Form einer Konstante
   *         Bookmark.POS_XXX (siehe Beschreibung)
   */
  public int compare(Bookmark b)
  {
    Logger.debug2("compare: " + this + " <--> " + b);
    return compareTextRanges(this.getTextRange(), b.getTextRange());
  }

  // Positionsangaben als R�ckgabewerte von compareTextRanges
  // F�lle: A:=a alleine, 8:=�berlagerung von a und b, B:=b alleine

  /**
   * Das Bookmark B tritt im Dokument vor dem Bookmark A auf.
   */
  public static final int POS_BBAA = -4;

  /**
   * Das Bookmark B startet vor dem Bookmark A, aber h�rt gleichzeitig mit A
   * auf.
   */
  public static final int POS_BB88 = -3;

  /**
   * Das Bookmark B enth�lt das Bookmark A vollst�ndig.
   */
  public static final int POS_B88B = -2;

  /**
   * Das Bookmark B startet mit dem Bookmark A, h�rt jedoch vor dem Bookmark A
   * auf.
   */
  public static final int POS_88AA = -1;

  /**
   * A und B liegen an der selben Position.
   */
  public static final int POS_8888 = -0;

  /**
   * Das Bookmark A startet mit dem Bookmark B, h�rt jedoch vor dem Bookmark B
   * auf.
   */
  public static final int POS_88BB = 1;

  /**
   * Das Bookmark A enth�lt das Bookmark B vollst�ndig.
   */
  public static final int POS_A88A = 2;

  /**
   * Das Bookmark A startet vor dem Bookmark B, h�rt jedoch gemeinsam mit dem
   * Bookmark B auf.
   */
  public static final int POS_AA88 = 3;

  /**
   * Das Bookmark A liegt im Dokument vor dem Bookmark B.
   */
  public static final int POS_AABB = 4;

  /**
   * Diese Methode vergleicht die beiden TextRanges a und b und liefert ihre
   * Relation in Form der Konstanten Bookmark.POS_xxx zur�ck.
   * 
   * @param a
   * @param b
   * @return Die Relation von a und b in Form einer Konstanten Bookmark.POS_xxx.
   */
  private static int compareTextRanges(XTextRange a, XTextRange b)
  {
    // F�lle: A:=a alleine, 8:=�berlagerung von a und b, B:=b alleine
    // -4 = BBBBAAAA bzw. BB88AA
    // -3 = BB88
    // -2 = B88B
    // -1 = 88AA
    // +0 = 8888
    // +1 = 88BB
    // +2 = A88A
    // +3 = AA88
    // +4 = AAAABBBB bzw. AA88BB

    XTextRangeCompare compare = (XTextRangeCompare) UnoRuntime.queryInterface(
        XTextRangeCompare.class,
        a.getText());
    if (compare != null && a != null && b != null)
    {
      try
      {
        int start = compare.compareRegionStarts(a, b) + 1;
        int end = compare.compareRegionEnds(a, b) + 1;
        return (3 * start + 1 * end) - 4;
      }
      catch (IllegalArgumentException e)
      {
        // nicht loggen! Tritt regul�r auf, wenn Bookmarks aus unterschiedlichen
        // Frames verglichen werden.
      }
    }
    // Im Fehlerfall wird so getan als k�me B nach A
    return POS_AABB;
  }

  /**
   * Diese Methode benennt das Bookmark oldName zu dem Namen newName um. Ist der
   * Name bereits definiert, so h�ngt OOo an den Namen automatisch eine Nummer
   * an. Die Methode gibt den tats�chlich erzeugten Bookmarknamen zur�ck.
   * 
   * @param newName
   * @return den tats�chlich erzeugten Namen des Bookmarks.
   * @throws Exception
   */
  public String rename(String newName)
  {
    Logger.debug("Rename \"" + name + "\" --> \"" + newName + "\"");

    XTextRange oldRange = getTextRange();
    if (oldRange != null)
    {
      // altes Bookmark l�schen:
      remove();

      // neues Bookmark mit neuem Namen hinzuf�gen.
      UnoService bookmark = new UnoService(null);
      try
      {
        bookmark = document.create("com.sun.star.text.Bookmark");
      }
      catch (Exception e)
      {
        Logger.error(e);
      }

      if (bookmark.xNamed() != null)
      {
        bookmark.xNamed().setName(newName);
        name = bookmark.xNamed().getName();
      }
      try
      {
        oldRange.getText().insertTextContent(
            oldRange,
            bookmark.xTextContent(),
            true);
      }
      catch (IllegalArgumentException e)
      {
        Logger.error(e);
      }
    }
    return name;
  }

  /**
   * Diese Methode weist dem Bookmark einen neuen TextRange (als Anchor) zu.
   * 
   * @param xTextRange
   *          Der neue TextRange des Bookmarks.
   */
  public void rerangeBookmark(XTextRange xTextRange)
  {
    // altes Bookmark l�schen.
    remove();

    // neues Bookmark unter dem alten Namen mit neuer Ausdehnung hinzuf�gen.
    try
    {
      UnoService bookmark = document.create("com.sun.star.text.Bookmark");
      bookmark.xNamed().setName(name);
      xTextRange.getText().insertTextContent(
          xTextRange,
          bookmark.xTextContent(),
          true);
    }
    catch (Exception e)
    {
      Logger.error(e);
    }
  }

  /**
   * Die Methode gibt die XTextRange des Bookmarks zur�ck, oder null, falls das
   * Bookmark nicht vorhanden ist (z,B, weil es inzwischen gel�scht wurde).
   * 
   * @return
   */
  public XTextRange getTextRange()
  {
    UnoService bookmark = getBookmarkService(name, document);

    if (bookmark.xTextContent() != null)
      return bookmark.xTextContent().getAnchor();
    return null;
  }

  /**
   * Diese Methode l�scht das Bookmark aus dem Dokument.
   */
  public void remove()
  {
    UnoService bookmark = getBookmarkService(name, document);
    if (bookmark.xTextContent() != null)
    {
      try
      {
        XTextRange range = bookmark.xTextContent().getAnchor();
        range.getText().removeTextContent(bookmark.xTextContent());
        bookmark = new UnoService(null);
      }
      catch (NoSuchElementException e)
      {
        Logger.error(e);
      }
    }
  }
}