package com.thaiopensource.relaxng.input.dtd;

import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.List;
import java.util.Iterator;

import com.thaiopensource.xml.dtd.om.*;
import com.thaiopensource.xml.em.ExternalId;
import com.thaiopensource.xml.util.WellKnownNamespaces;
import com.thaiopensource.util.Localizer;
import com.thaiopensource.relaxng.output.common.ErrorReporter;
import com.thaiopensource.relaxng.edit.Pattern;
import com.thaiopensource.relaxng.edit.TextPattern;
import com.thaiopensource.relaxng.edit.NotAllowedPattern;
import com.thaiopensource.relaxng.edit.ChoicePattern;
import com.thaiopensource.relaxng.edit.EmptyPattern;
import com.thaiopensource.relaxng.edit.GroupPattern;
import com.thaiopensource.relaxng.edit.OneOrMorePattern;
import com.thaiopensource.relaxng.edit.ZeroOrMorePattern;
import com.thaiopensource.relaxng.edit.OptionalPattern;
import com.thaiopensource.relaxng.edit.RefPattern;
import com.thaiopensource.relaxng.edit.DataPattern;
import com.thaiopensource.relaxng.edit.ValuePattern;
import com.thaiopensource.relaxng.edit.AttributePattern;
import com.thaiopensource.relaxng.edit.AttributeAnnotation;
import com.thaiopensource.relaxng.edit.NameClass;
import com.thaiopensource.relaxng.edit.Container;
import com.thaiopensource.relaxng.edit.DefineComponent;
import com.thaiopensource.relaxng.edit.Combine;
import com.thaiopensource.relaxng.edit.ElementPattern;
import com.thaiopensource.relaxng.edit.GrammarPattern;
import com.thaiopensource.relaxng.edit.IncludeComponent;
import com.thaiopensource.relaxng.edit.NameNameClass;
import com.thaiopensource.relaxng.edit.SchemaCollection;
import com.thaiopensource.relaxng.parse.SchemaBuilder;
import org.xml.sax.SAXException;

public class Converter {
  private ErrorReporter er;
  private SchemaCollection sc = new SchemaCollection();
  private boolean inlineAttlistDecls = false;
  private boolean hadAny = false;
  private boolean hadDefaultValue = false;
  private Map elementNameTable = new Hashtable();
  private Map attlistDeclTable = new Hashtable();
  private Map defTable = new Hashtable();
  private Map prefixTable = new Hashtable();
  private String initialComment = null;

  private Map duplicateAttributeTable = new Hashtable();
  private Map currentDuplicateAttributeTable = null;
  private String defaultNamespace = null;
  private String annotationPrefix = null;

  // These variables control the names use for definitions.
  private String colonReplacement = null;
  private String elementDeclPattern;
  private String attlistDeclPattern;
  private String anyName;

  private static final int ELEMENT_DECL = 01;
  private static final int ATTLIST_DECL = 02;
  private static final int ELEMENT_REF = 04;

  private static final String SEPARATORS = ".-_";

  private static final String COMPATIBILITY_ANNOTATIONS_URI
    = "http://relaxng.org/ns/compatibility/annotations/1.0";

  // # is the category; % is the name in the category

  private static final String DEFAULT_PATTERN = "#.%";

  private String[] ELEMENT_KEYWORDS = {
    "element", "elem", "e"
  };

  private String[] ATTLIST_KEYWORDS = {
    "attlist", "attributes", "attribs", "atts", "a"
  };

  private String[] ANY_KEYWORDS = {
    "any", "ANY", "anyElement"
  };

  private static abstract class VisitorBase implements TopLevelVisitor {
    public void processingInstruction(String target, String value) throws Exception { }
    public void comment(String value) throws Exception { }
    public void flagDef(String name, Flag flag) throws Exception { }
    public void includedSection(Flag flag, TopLevel[] contents)
      throws Exception {
      for (int i = 0; i < contents.length; i++)
	contents[i].accept(this);
    }

    public void ignoredSection(Flag flag, String contents) throws Exception { }
    public void internalEntityDecl(String name, String value) throws Exception { }
    public void externalEntityDecl(String name, ExternalId externalId) throws Exception { }
    public void notationDecl(String name, ExternalId externalId) throws Exception { }
    public void nameSpecDef(String name, NameSpec nameSpec) throws Exception { }
    public void overriddenDef(Def def, boolean isDuplicate) throws Exception { }
    public void externalIdDef(String name, ExternalId externalId) throws Exception { }
    public void externalIdRef(String name, ExternalId externalId,
			      String uri, String encoding, TopLevel[] contents)
      throws Exception {
      for (int i = 0; i < contents.length; i++)
	contents[i].accept(this);
    }
    public void paramDef(String name, String value) throws Exception { }
    public void attributeDefaultDef(String name, AttributeDefault ad) throws Exception { }
  }


  private class Analyzer extends VisitorBase implements ModelGroupVisitor,
							AttributeGroupVisitor {
    public void elementDecl(NameSpec nameSpec, ModelGroup modelGroup)
      throws Exception {
      noteElementName(nameSpec.getValue(), ELEMENT_DECL);
      modelGroup.accept(this);
    }

    public void attlistDecl(NameSpec nameSpec, AttributeGroup attributeGroup)
      throws Exception {
      noteElementName(nameSpec.getValue(), ATTLIST_DECL);
      if (inlineAttlistDecls)
        noteAttlist(nameSpec.getValue(), attributeGroup);
      attributeGroup.accept(this);
    }

    public void modelGroupDef(String name, ModelGroup modelGroup)
      throws Exception {
      noteDef(name);
      modelGroup.accept(this);
    }

    public void attributeGroupDef(String name, AttributeGroup attributeGroup)
      throws Exception {
      noteDef(name);
      attributeGroup.accept(this);
    }

    public void enumGroupDef(String name, EnumGroup enumGroup) {
      noteDef(name);
    }

    public void datatypeDef(String name, Datatype datatype) {
      noteDef(name);
    }

    public void choice(ModelGroup[] members) throws Exception {
      for (int i = 0; i < members.length; i++)
	members[i].accept(this);
    }

    public void sequence(ModelGroup[] members) throws Exception {
      for (int i = 0; i < members.length; i++)
	members[i].accept(this);
    }

    public void oneOrMore(ModelGroup member) throws Exception {
      member.accept(this);
    }

    public void zeroOrMore(ModelGroup member) throws Exception {
      member.accept(this);
    }

    public void optional(ModelGroup member) throws Exception {
      member.accept(this);
    }

    public void modelGroupRef(String name, ModelGroup modelGroup) {
    }

    public void elementRef(NameSpec name) {
      noteElementName(name.getValue(), ELEMENT_REF);
    }

    public void pcdata() {
    }

    public void any() {
      hadAny = true;
    }

    public void attribute(NameSpec nameSpec,
			  Datatype datatype,
			  AttributeDefault attributeDefault) {
      noteAttribute(nameSpec.getValue(), attributeDefault.getDefaultValue());
    }

    public void attributeGroupRef(String name, AttributeGroup attributeGroup) {
    }

  }


  private class ComponentOutput extends VisitorBase {
    private final List components;

    ComponentOutput(Container container) {
      components = container.getComponents();
    }

    public void elementDecl(NameSpec nameSpec, ModelGroup modelGroup) throws Exception {
      GroupPattern gp = new GroupPattern();
      if (inlineAttlistDecls) {
        List groups = (List)attlistDeclTable.get(nameSpec.getValue());
        if (groups != null) {
          currentDuplicateAttributeTable = new Hashtable();
          AttributeGroupVisitor agv = new AttributeGroupOutput(gp);
          for (Iterator iter = groups.iterator(); iter.hasNext();)
            ((AttributeGroup)iter.next()).accept(agv);
        }
      }
      else
        gp.getChildren().add(ref(attlistDeclName(nameSpec.getValue())));
      Pattern pattern = convert(modelGroup);
      if (gp.getChildren().size() > 0) {
        if (pattern instanceof GroupPattern)
          gp.getChildren().addAll(((GroupPattern)pattern).getChildren());
        else
          gp.getChildren().add(pattern);
        pattern = gp;
      }
      components.add(new DefineComponent(elementDeclName(nameSpec.getValue()),
                                         new ElementPattern(convertQName(nameSpec.getValue(), true),
                                                            pattern)));
      if (!inlineAttlistDecls && (nameFlags(nameSpec.getValue()) & ATTLIST_DECL) == 0) {
        DefineComponent dc = new DefineComponent(attlistDeclName(nameSpec.getValue()), new EmptyPattern());
        dc.setCombine(Combine.INTERLEAVE);
        components.add(dc);
      }
      if (anyName != null) {
        DefineComponent dc = new DefineComponent(anyName, ref(elementDeclName(nameSpec.getValue())));
        dc.setCombine(Combine.CHOICE);
        components.add(dc);
      }
    }

    public void attlistDecl(NameSpec nameSpec, AttributeGroup attributeGroup) throws Exception {
      if (inlineAttlistDecls)
        return;
      String name = nameSpec.getValue();
      currentDuplicateAttributeTable
	= (Map)duplicateAttributeTable.get(name);
      if (currentDuplicateAttributeTable == null) {
	currentDuplicateAttributeTable = new Hashtable();
	duplicateAttributeTable.put(name, currentDuplicateAttributeTable);
      }
      else {
        EmptyAttributeGroupDetector emptyDetector = new EmptyAttributeGroupDetector();
        attributeGroup.accept(emptyDetector);
        if (!emptyDetector.containsAttribute)
          return;
      }
      DefineComponent dc = new DefineComponent(attlistDeclName(name), convert(attributeGroup));
      dc.setCombine(Combine.INTERLEAVE);
      components.add(dc);
    }

    public void modelGroupDef(String name, ModelGroup modelGroup)
      throws Exception {
      components.add(new DefineComponent(name, convert(modelGroup)));
    }

    public void attributeGroupDef(String name, AttributeGroup attributeGroup)
      throws Exception {
      // This takes care of duplicates within the group
      currentDuplicateAttributeTable = new Hashtable();
      Pattern pattern;
      AttributeGroupMember[] members = attributeGroup.getMembers();
      if (members.length == 0)
        pattern = new EmptyPattern();
      else {
        GroupPattern group = new GroupPattern();
        AttributeGroupVisitor agv = new AttributeGroupOutput(group);
	for (int i = 0; i < members.length; i++)
	  members[i].accept(agv);
        if (group.getChildren().size() == 1)
          pattern = (Pattern)group.getChildren().get(0);
        else
          pattern = group;
      }
      components.add(new DefineComponent(name, pattern));
    }

    public void enumGroupDef(String name, EnumGroup enumGroup) throws Exception {
      ChoicePattern choice = new ChoicePattern();
      enumGroup.accept(new EnumGroupOutput(choice));
      Pattern pattern;
      if (choice.getChildren().size() == 1)
        pattern = (Pattern)choice.getChildren().get(0);
      else
        pattern = choice;
      components.add(new DefineComponent(name, pattern));
    }

    public void datatypeDef(String name, Datatype datatype) throws Exception {
      components.add(new DefineComponent(name, convert(datatype)));
    }

    public void comment(String value) {
      // XXX
    }

    public void externalIdRef(String name, ExternalId externalId,
			      String uri, String encoding, TopLevel[] contents)
      throws Exception {
      if (uri == null) {
	super.externalIdRef(name, externalId, uri, encoding, contents);
	return;
      }
      SignificanceDetector sd = new SignificanceDetector();
      try {
	sd.externalIdRef(name, externalId, uri, encoding, contents);
	if (!sd.significant)
	  return;
      }
      catch (Exception e) {
	throw (RuntimeException)e;
      }
      IncludeComponent ic = new IncludeComponent(uri);
      ic.setNs(defaultNamespace);
      components.add(ic);
      GrammarPattern included = new GrammarPattern();
      TopLevelVisitor tlv = new ComponentOutput(included);
      for (int i = 0; i < contents.length; i++)
        contents[i].accept(tlv);
      // XXX what if included multiple times
      sc.getSchemas().put(uri, included);
    }

  }

  private class AttributeGroupOutput implements AttributeGroupVisitor {
    List group;

    AttributeGroupOutput(GroupPattern gp) {
      group = gp.getChildren();
    }

    public void attribute(NameSpec nameSpec,
                          Datatype datatype,
                          AttributeDefault attributeDefault) throws Exception {
      String name = nameSpec.getValue();
      if (currentDuplicateAttributeTable.get(name) != null)
        return;
      currentDuplicateAttributeTable.put(name, name);
      if (name.equals("xmlns") || name.startsWith("xmlns:")) {
        group.add(new EmptyPattern());
        return;
      }
      String dv = attributeDefault.getDefaultValue();
      String fv = attributeDefault.getFixedValue();
      Pattern dt;
      if (fv != null) {
        String[] typeName = valueType(datatype);
        dt = new ValuePattern(typeName[0], typeName[1], fv);
      }
      else if (datatype.getType() != Datatype.CDATA)
        dt = convert(datatype);
      else
        dt = new TextPattern();
      AttributePattern pattern = new AttributePattern(convertQName(name, false), dt);
      if (dv != null) {
        AttributeAnnotation anno = new AttributeAnnotation(WellKnownNamespaces.RELAX_NG_COMPATIBILITY_ANNOTATIONS, "defaultValue", dv);
        anno.setPrefix(annotationPrefix);
        pattern.getAttributeAnnotations().add(anno);
      }
      if (!attributeDefault.isRequired())
        group.add(new OptionalPattern(pattern));
      else
        group.add(pattern);
    }

    public void attributeGroupRef(String name, AttributeGroup attributeGroup)
            throws Exception {
      DuplicateAttributeDetector detector = new DuplicateAttributeDetector();
      attributeGroup.accept(detector);
      if (detector.containsDuplicate)
        attributeGroup.accept(this);
      else {
        group.add(ref(name));
        for (Iterator iter = detector.names.iterator(); iter.hasNext();) {
          String tem = (String)iter.next();
          currentDuplicateAttributeTable.put(tem, tem);
        }
      }
    }


   }

  private class DatatypeOutput implements DatatypeVisitor {
    Pattern pattern;

    public void cdataDatatype() {
      pattern = new DataPattern("", "string");
    }

    public void tokenizedDatatype(String typeName) {
      pattern = new DataPattern(WellKnownNamespaces.XML_SCHEMA_DATATYPES, typeName);
    }

    public void enumDatatype(EnumGroup enumGroup) throws Exception {
      if (enumGroup.getMembers().length == 0)
        pattern = new NotAllowedPattern();
      else {
        ChoicePattern tem = new ChoicePattern();
        pattern = tem;
        enumGroup.accept(new EnumGroupOutput(tem));
      }
    }

    public void notationDatatype(EnumGroup enumGroup) throws Exception {
      enumDatatype(enumGroup);
    }

    public void datatypeRef(String name, Datatype datatype) {
      pattern = ref(name);
    }
  }

  private class EnumGroupOutput implements EnumGroupVisitor {
    final private List list;

    EnumGroupOutput(ChoicePattern choice) {
      list = choice.getChildren();
    }

    public void enumValue(String value) {
      list.add(new ValuePattern("", "token", value));
     }

     public void enumGroupRef(String name, EnumGroup enumGroup) {
       list.add(ref(name));
     }
  }

  private class ModelGroupOutput implements ModelGroupVisitor {
    private Pattern pattern;

    public void choice(ModelGroup[] members) throws Exception {
      if (members.length == 0)
        pattern = new NotAllowedPattern();
      else if (members.length == 1)
	members[0].accept(this);
      else {
        ChoicePattern tem = new ChoicePattern();
        pattern = tem;
        List children = tem.getChildren();
	for (int i = 0; i < members.length; i++)
          children.add(convert(members[i]));
      }
    }

    public void sequence(ModelGroup[] members) throws Exception {
      if (members.length == 0)
        pattern = new EmptyPattern();
      else if (members.length == 1)
	members[0].accept(this);
      else {
        GroupPattern tem = new GroupPattern();
        pattern = tem;
        List children = tem.getChildren();
	for (int i = 0; i < members.length; i++)
	  children.add(convert(members[i]));
      }
    }

    public void oneOrMore(ModelGroup member) throws Exception {
      pattern = new OneOrMorePattern(convert(member));
    }

    public void zeroOrMore(ModelGroup member) throws Exception {
      pattern = new ZeroOrMorePattern(convert(member));
    }

    public void optional(ModelGroup member) throws Exception {
      pattern = new OptionalPattern(convert(member));
    }

    public void modelGroupRef(String name, ModelGroup modelGroup) {
      pattern = ref(name);
    }

    public void elementRef(NameSpec name) {
      pattern = ref(elementDeclName(name.getValue()));
    }

    public void pcdata() {
      pattern = new TextPattern();
    }

    public void any() {
      pattern = new ZeroOrMorePattern(ref(anyName));
    }

  }


  private class DuplicateAttributeDetector implements AttributeGroupVisitor {
    private boolean containsDuplicate = false;
    private List names = new Vector();

    public void attribute(NameSpec nameSpec,
			  Datatype datatype,
			  AttributeDefault attributeDefault) {
      String name = nameSpec.getValue();
      if (currentDuplicateAttributeTable.get(name) != null)
	containsDuplicate = true;
      names.add(name);
    }

    public void attributeGroupRef(String name, AttributeGroup attributeGroup) throws Exception {
      attributeGroup.accept(this);
    }

  }

  private class EmptyAttributeGroupDetector implements AttributeGroupVisitor {
    private boolean containsAttribute = false;

    public void attribute(NameSpec nameSpec,
                          Datatype datatype,
                          AttributeDefault attributeDefault) {
      if (currentDuplicateAttributeTable.get(nameSpec.getValue()) == null)
        containsAttribute = true;
    }

    public void attributeGroupRef(String name, AttributeGroup attributeGroup) throws Exception {
      attributeGroup.accept(this);
    }
  }

  private class SignificanceDetector extends VisitorBase {
    boolean significant = false;
    public void elementDecl(NameSpec nameSpec, ModelGroup modelGroup)
      throws Exception {
      significant = true;
    }

    public void attlistDecl(NameSpec nameSpec, AttributeGroup attributeGroup)
      throws Exception {
      significant = true;
    }

    public void modelGroupDef(String name, ModelGroup modelGroup)
      throws Exception {
      significant = true;
    }

    public void attributeGroupDef(String name, AttributeGroup attributeGroup)
      throws Exception {
      significant = true;
    }

    public void enumGroupDef(String name, EnumGroup enumGroup) {
      significant = true;
    }

    public void datatypeDef(String name, Datatype datatype) {
      significant = true;
    }
  }

  public Converter(ErrorReporter er) {
    this.er = er;
  }

  public SchemaCollection convertDtd(Dtd dtd) throws SAXException {
    try {
      dtd.accept(new Analyzer());
      chooseNames();
      if (defaultNamespace == null)
        defaultNamespace = SchemaBuilder.INHERIT_NS;
      GrammarPattern grammar = new GrammarPattern();
      sc.setMainSchema(grammar);
      dtd.accept(new ComponentOutput(grammar));
      outputUndefinedElements(grammar.getComponents());
      outputStart(grammar.getComponents());
      return sc;
    }
    catch (Exception e) {
      throw (RuntimeException)e;
    }
  }

  private void chooseNames() {
    chooseAny();
    chooseColonReplacement();
    chooseDeclPatterns();
    chooseAnnotationPrefix();
  }

  private void chooseAny() {
    if (!hadAny)
      return;
    for (int n = 0;; n++) {
      for (int i = 0; i < ANY_KEYWORDS.length; i++) {
	anyName = repeatChar('_', n) + ANY_KEYWORDS[i];
	if (defTable.get(anyName) == null) {
	  defTable.put(anyName, anyName);
	  return;
	}
      }
    }
  }

  private void chooseAnnotationPrefix() {
    if (!hadDefaultValue)
      return;
    for (int n = 0;; n++) {
      annotationPrefix = repeatChar('_', n) + "a";
      if (prefixTable.get(annotationPrefix) == null)
	return;
    }
  }

  private void chooseColonReplacement() {
    if (colonReplacementOk())
      return;
    for (int n = 1;; n++) {
      for (int i = 0; i < SEPARATORS.length(); i++) {
	colonReplacement = repeatChar(SEPARATORS.charAt(i), n);
	if (colonReplacementOk())
	  return;
      }
    }
  }

  private boolean colonReplacementOk() {
    Hashtable table = new Hashtable();
    for (Iterator iter = elementNameTable.keySet().iterator(); iter.hasNext();) {
      String name = mungeQName((String)iter.next());
      if (table.get(name) != null)
	return false;
      table.put(name, name);
    }
    return true;
  }

  private void chooseDeclPatterns() {
    // XXX Try to match length and case of best prefix
    String pattern = namingPattern();
    if (patternOk("%"))
      elementDeclPattern = "%";
    else
      elementDeclPattern = choosePattern(pattern, ELEMENT_KEYWORDS);
    attlistDeclPattern = choosePattern(pattern, ATTLIST_KEYWORDS);
  }

  private String choosePattern(String metaPattern, String[] keywords) {
    for (;;) {
      for (int i = 0; i < keywords.length; i++) {
	String pattern = substitute(metaPattern, '#', keywords[i]);
	if (patternOk(pattern))
	  return pattern;
      }
      // add another separator
      metaPattern = (metaPattern.substring(0, 1)
		     + metaPattern.substring(1, 2)
		     + metaPattern.substring(1, 2)
		     + metaPattern.substring(2));
    }
  }

  private String namingPattern() {
    Map patternTable = new Hashtable();
    for (Iterator iter = defTable.keySet().iterator();
	 iter.hasNext();) {
      String name = (String)iter.next();
      for (int i = 0; i < SEPARATORS.length(); i++) {
	char sep = SEPARATORS.charAt(i);
	int k = name.indexOf(sep);
	if (k > 0)
	  inc(patternTable, name.substring(0, k + 1) + "%");
	k = name.lastIndexOf(sep);
	if (k >= 0 && k < name.length() - 1)
	  inc(patternTable, "%" + name.substring(k));
      }
    }
    String bestPattern = null;
    int bestCount = 0;
    for (Iterator iter = patternTable.entrySet().iterator();
	 iter.hasNext();) {
      Map.Entry entry = (Map.Entry)iter.next();
      int count = ((Integer)entry.getValue()).intValue();
      if (bestPattern == null || count > bestCount) {
	bestCount = count;
	bestPattern = (String)entry.getKey();
      }
    }
    if (bestPattern == null)
      return DEFAULT_PATTERN;
    if (bestPattern.charAt(0) == '%')
      return bestPattern.substring(0, 2) + "#";
    else
      return "#" + bestPattern.substring(bestPattern.length() - 2);
  }

  private static void inc(Map table, String str) {
    Integer n = (Integer)table.get(str);
    if (n == null)
      table.put(str, new Integer(1));
    else
      table.put(str, new Integer(n.intValue() + 1));
  }

  private boolean patternOk(String pattern) {
    for (Iterator iter = elementNameTable.keySet().iterator();
	 iter.hasNext();) {
      String name = mungeQName((String)iter.next());
      if (defTable.get(substitute(pattern, '%', name)) != null)
	return false;
    }
    return true;
  }

  private void noteDef(String name) {
    defTable.put(name, name);
  }

  private void noteElementName(String name, int flags) {
    Integer n = (Integer)elementNameTable.get(name);
    if (n != null) {
      flags |= n.intValue();
      if (n.intValue() == flags)
	return;
    }
    else
      noteNamePrefix(name);
    elementNameTable.put(name, new Integer(flags));
  }

  private void noteAttlist(String name, AttributeGroup group) {
    List groups = (List)attlistDeclTable.get(name);
    if (groups == null) {
      groups = new Vector();
      attlistDeclTable.put(name, groups);
    }
    groups.add(group);
  }

  private void noteAttribute(String name, String defaultValue) {
    if (name.equals("xmlns")) {
      if (defaultValue != null) {
	if (defaultNamespace != null
	    && !defaultNamespace.equals(defaultValue))
	  error("INCONSISTENT_DEFAULT_NAMESPACE");
	else
	  defaultNamespace = defaultValue;
      }
    }
    else if (name.startsWith("xmlns:")) {
      if (defaultValue != null) {
	String prefix = name.substring(6);
	String ns = (String)prefixTable.get(prefix);
	if (ns != null
	    && !ns.equals("")
	    && !ns.equals(defaultValue))
	  error("INCONSISTENT_PREFIX", prefix);
	else if (!prefix.equals("xml"))
	  prefixTable.put(prefix, defaultValue);
      }
    }
    else {
      if (defaultValue != null)
	hadDefaultValue = true;
      noteNamePrefix(name);
    }
  }

  private void noteNamePrefix(String name) {
    int i = name.indexOf(':');
    if (i < 0)
      return;
    String prefix = name.substring(0, i);
    if (prefixTable.get(prefix) == null && !prefix.equals("xml"))
      prefixTable.put(prefix, "");
  }

  private int nameFlags(String name) {
    Integer n = (Integer)elementNameTable.get(name);
    if (n == null)
      return 0;
    return n.intValue();
  }

  private String elementDeclName(String name) {
    return substitute(elementDeclPattern, '%', mungeQName(name));
  }

  private String attlistDeclName(String name) {
    return substitute(attlistDeclPattern, '%', mungeQName(name));
  }

  private String mungeQName(String name) {
    if (colonReplacement == null) {
      int i = name.indexOf(':');
      if (i < 0)
	return name;
      return name.substring(i + 1);
    }
    return substitute(name, ':', colonReplacement);
  }

  private static String repeatChar(char c, int n) {
    char[] buf = new char[n];
    for (int i = 0; i < n; i++)
      buf[i] = c;
    return new String(buf);
  }

  /* Replace the first occurrence of ch in pattern by value. */

  private static String substitute(String pattern, char ch, String value) {
    int i = pattern.indexOf(ch);
    if (i < 0)
      return pattern;
    StringBuffer buf = new StringBuffer();
    buf.append(pattern.substring(0, i));
    buf.append(value);
    buf.append(pattern.substring(i + 1));
    return buf.toString();
  }

  private void outputStart(List components) {
    ChoicePattern choice = new ChoicePattern();
    components.add(new DefineComponent(DefineComponent.START, choice));
    // Use the defined but unreferenced elements.
    // If there aren't any, use all defined elements.
    int mask = ELEMENT_REF|ELEMENT_DECL;
    for (;;) {
      boolean gotOne = false;
      for (Iterator iter = elementNameTable.entrySet().iterator();
	   iter.hasNext();) {
        Map.Entry entry = (Map.Entry)iter.next();
	if ((((Integer)entry.getValue()).intValue() & mask) == ELEMENT_DECL) {
	  gotOne = true;
	  choice.getChildren().add(ref(elementDeclName((String)entry.getKey())));
	}
      }
      if (gotOne)
	break;
      if (mask == ELEMENT_DECL)
	break;
      mask = ELEMENT_DECL;
    }
    if (anyName != null) {
      DefineComponent dc = new DefineComponent(anyName, new TextPattern());
      dc.setCombine(Combine.CHOICE);
      components.add(dc);
    }
  }


  private void outputUndefinedElements(List components) {
    for (Iterator iter = elementNameTable.entrySet().iterator(); iter.hasNext();) {
      Map.Entry entry = (Map.Entry)iter.next();
      if ((((Integer)entry.getValue()).intValue() & ELEMENT_DECL)
	  == 0) {
        DefineComponent dc = new DefineComponent(elementDeclName((String)entry.getKey()), new NotAllowedPattern());
        dc.setCombine(Combine.CHOICE);
        components.add(dc);
      }
    }
  }

  static private Pattern ref(String name) {
    return new RefPattern(name);
  }

  private void error(String key) {
    er.error(key, null);
  }

  private void error(String key, String arg) {
    er.error(key, arg, null);
  }

  private void warning(String key) {
    er.warning(key, null);
  }

  private void warning(String key, String arg) {
    er.warning(key, arg, null);
  }

  private static String[] valueType(Datatype datatype) {
    datatype = datatype.deref();
    switch (datatype.getType()) {
    case Datatype.CDATA:
      return new String[] { "", "string" };
    case Datatype.TOKENIZED:
      return new String[] { WellKnownNamespaces.XML_SCHEMA_DATATYPES, ((TokenizedDatatype)datatype).getTypeName() };
    }
    return new String[] { "", "token" };
  }

  private Pattern convert(ModelGroup mg) throws Exception {
    ModelGroupOutput mgo = new ModelGroupOutput();
    mg.accept(mgo);
    return mgo.pattern;
  }

  private Pattern convert(Datatype dt) throws Exception {
    DatatypeOutput dto = new DatatypeOutput();
    dt.accept(dto);
    return dto.pattern;
  }

  private Pattern convert(AttributeGroup ag) throws Exception {
    GroupPattern group = new GroupPattern();
    ag.accept(new AttributeGroupOutput(group));
    if (group.getChildren().size() == 1)
      return (Pattern)group.getChildren().get(0);
    return group;
  }

  private NameClass convertQName(String name, boolean useDefault) {
    int i = name.indexOf(':');
    if (i < 0)
      return new NameNameClass(useDefault ? defaultNamespace : "", name);
    String prefix = name.substring(0, i);
    String localName = name.substring(i + 1);
    String ns;
    if (prefix.equals("xml"))
      ns = WellKnownNamespaces.XML;
    else {
      ns = (String)prefixTable.get(prefix);
      if (ns.equals("")) {
        error("UNDECLARED_PREFIX", prefix);
        ns = prefix;
      }
    }
    NameNameClass nnc = new NameNameClass(ns, localName);
    nnc.setPrefix(prefix);
    return nnc;
  }
}
