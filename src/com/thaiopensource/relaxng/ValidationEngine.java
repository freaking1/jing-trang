package com.thaiopensource.relaxng;

import java.io.IOException;

import org.xml.sax.SAXException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.thaiopensource.datatype.DatatypeFactory;
import com.thaiopensource.datatype.DatatypeAssignment;

public class ValidationEngine {
  private XMLReaderCreator xrc;
  private XMLReader xr;
  private ErrorHandler eh;
  private DatatypeFactory df;
  private PatternBuilder pb = new PatternBuilder();
  private Pattern p;
  private boolean isAmbig;

  public void setXMLReaderCreator(XMLReaderCreator xrc) {
    this.xrc = xrc;
    if (eh != null)
      xr.setErrorHandler(eh);
  }
  
  /**
   * A call to setErrorHandler can be made at any time.
   */
  public void setErrorHandler(ErrorHandler eh) {
    this.eh = eh;
    if (xr != null)
      xr.setErrorHandler(eh);
  }

  public void setDatatypeFactory(DatatypeFactory df) {
    this.df = df;
  }

  /**
   * setXMLReaderCreator must be called before any call to loadPattern
   */
  public boolean loadPattern(InputSource in) throws SAXException, IOException {
    xr = xrc.createXMLReader();
    xr.setErrorHandler(eh);
    p = PatternReader.readPattern(xrc, xr, pb, df, in);
    if (p == null)
      return false;
    isAmbig = new DatatypeAssignmentChecker(pb, p, eh).isAmbig();
    return true;
  }

  /**
   * loadPattern must be called before any call to validate
   */
  public boolean validate(InputSource in) throws SAXException, IOException {
    DatatypeAssignment da;
    if (isAmbig)
      da = null;
    else
      da = df.createDatatypeAssignment(xr);
    Validator v = new Validator(p, pb, xr, da);
    xr.parse(in);
    return v.getValid();
  }

  /**
   * loadPattern must be called before any call to validateMultiThread
   * validateMultiThread can safely be called for a single
   * ValidationEngine from multiple threads simultaneously
   */
  public boolean validateMultiThread(InputSource in)
    throws SAXException, IOException {
    XMLReader xr = xrc.createXMLReader();
    DatatypeAssignment da;
    if (isAmbig)
      da = null;
    else
      da = df.createDatatypeAssignment(xr);
    Validator v = new Validator(p, new PatternBuilder(pb), xr, da);
    xr.parse(in);
    return v.getValid();
  }
}
