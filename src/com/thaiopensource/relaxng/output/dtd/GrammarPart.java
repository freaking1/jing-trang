package com.thaiopensource.relaxng.output.dtd;

import com.thaiopensource.relaxng.edit.GrammarPattern;
import com.thaiopensource.relaxng.edit.Pattern;
import com.thaiopensource.relaxng.edit.Container;
import com.thaiopensource.relaxng.edit.Component;
import com.thaiopensource.relaxng.edit.DivComponent;
import com.thaiopensource.relaxng.edit.DefineComponent;
import com.thaiopensource.relaxng.edit.IncludeComponent;
import com.thaiopensource.relaxng.edit.ComponentVisitor;
import com.thaiopensource.relaxng.edit.SchemaCollection;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

class GrammarPart implements ComponentVisitor {
  private ErrorReporter er;
  private Map defines;
  private SchemaCollection schemas;
  private Map parts;
  // maps name to component that provides it
  private Map whereProvided = new HashMap();

  GrammarPart(ErrorReporter er, Map defines, SchemaCollection schemas, Map parts, GrammarPattern p) {
    this.er = er;
    this.defines = defines;
    this.schemas = schemas;
    this.parts = parts;
    visitContainer(p);
  }

  private GrammarPart(GrammarPart part, GrammarPattern p) {
    er = part.er;
    defines = part.defines;
    schemas = part.schemas;
    visitContainer(p);
  }

  Set providedSet() {
    return whereProvided.keySet();
  }

  public Object visitContainer(Container c) {
    List list = c.getComponents();
    for (int i = 0, len = list.size(); i < len; i++)
      ((Component)list.get(i)).accept(this);
    return null;
  }

  public Object visitDiv(DivComponent c) {
    return visitContainer(c);
  }

  public Object visitDefine(DefineComponent c) {
    if (defines.get(c.getName()) != null)
      er.error("sorry_multiple", c.getSourceLocation());
    else {
      defines.put(c.getName(), c.getBody());
      whereProvided.put(c.getName(), c);
    }
    return null;
  }

  public Object visitInclude(IncludeComponent c) {
    GrammarPattern p = (GrammarPattern)schemas.getSchemas().get(c.getHref());
    GrammarPart part = new GrammarPart(this, p);
    parts.put(c.getHref(), part);
    for (Iterator iter = part.providedSet().iterator(); iter.hasNext();)
      whereProvided.put((String)iter.next(), c);
    return null;
  }

  Component getWhereProvided(String paramEntityName) {
    return (Component)whereProvided.get(paramEntityName);
  }
}
