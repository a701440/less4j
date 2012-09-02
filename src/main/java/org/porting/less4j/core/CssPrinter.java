package org.porting.less4j.core;

import java.util.Iterator;
import java.util.List;

import org.porting.less4j.ILessCompiler;
import org.porting.less4j.core.ast.ASTCssNode;
import org.porting.less4j.core.ast.CharsetDeclaration;
import org.porting.less4j.core.ast.ColorExpression;
import org.porting.less4j.core.ast.Comment;
import org.porting.less4j.core.ast.ComposedExpression;
import org.porting.less4j.core.ast.CssClass;
import org.porting.less4j.core.ast.CssString;
import org.porting.less4j.core.ast.Declaration;
import org.porting.less4j.core.ast.DeclarationsBody;
import org.porting.less4j.core.ast.ExpressionOperator;
import org.porting.less4j.core.ast.FontFace;
import org.porting.less4j.core.ast.FunctionExpression;
import org.porting.less4j.core.ast.IdSelector;
import org.porting.less4j.core.ast.IdentifierExpression;
import org.porting.less4j.core.ast.Media;
import org.porting.less4j.core.ast.Medium;
import org.porting.less4j.core.ast.NamedColorExpression;
import org.porting.less4j.core.ast.NamedExpression;
import org.porting.less4j.core.ast.Nth;
import org.porting.less4j.core.ast.NumberExpression;
import org.porting.less4j.core.ast.PseudoClass;
import org.porting.less4j.core.ast.PseudoElement;
import org.porting.less4j.core.ast.RuleSet;
import org.porting.less4j.core.ast.Selector;
import org.porting.less4j.core.ast.SelectorAttribute;
import org.porting.less4j.core.ast.SelectorCombinator;
import org.porting.less4j.core.ast.SelectorOperator;
import org.porting.less4j.core.ast.SimpleSelector;
import org.porting.less4j.core.ast.StyleSheet;
import org.porting.less4j.core.parser.ANTLRParser;
import org.porting.less4j.core.parser.ASTBuilder;

/** FIXME: document: nth-is translated differently than less.js does. We strip whitespaces and they do not.
 * Input: 
:nth-child( 3n + 1 )
:nth-child( +3n - 2 )
:nth-child( -n+ 6)
:nth-child( +6 )
:nth-child( -6 ) {  
  padding: 2;
}

 * Less.js output: :nth-child( 3n + 1 ):nth-child( +3n - 2 ):nth-child( -n+ 6):nth-child( +6 ):nth-child( -6 ) {
  padding: 2;

 * Our output: :nth-child(3n+1) :nth-child(+3n-2) :nth-child(-n+6) :nth-child(+6) :nth-child(-6) {
  padding: 2;
}

 * 
 */
//FIXME document: not matching spaces especially around terms expressions and comments
public class CssPrinter implements ILessCompiler {
  private ANTLRParser parser = new ANTLRParser();
  private ASTBuilder astBuilder = new ASTBuilder();

  @Override
  public String compile(String content) {
    // FIXME: ugly, clean hierarchy and dependencies
    ExtendedStringBuilder stringBuilder = new ExtendedStringBuilder("");

    ANTLRParser.ParseResult result = parser.parseStyleSheet(content);
    StyleSheet styleSheet = astBuilder.parse(result.getTree());
    Builder builder = new Builder(stringBuilder);
    builder.append(styleSheet);
    return stringBuilder.toString();
  };

}

class Builder {

  private final ExtendedStringBuilder builder;

  public Builder(ExtendedStringBuilder builder) {
    super();
    this.builder = builder;
  }

  /**
   * returns whether the output changed as a result of the operation
   * 
   * @param node
   * @return
   */
  public boolean append(ASTCssNode node) {
    // opening comments should not be docked directly in front of following
    // thing
    appendComments(node.getOpeningComments(), true);
    boolean result = switchOnType(node);
    appendComments(node.getTrailingComments(), false);
    return result;
  }

  public boolean switchOnType(ASTCssNode node) {
    switch (node.getType()) {
    case RULE_SET:
      return appendRuleset((RuleSet) node);

    case DECLARATIONS_BODY:
      return appendDeclarationsBody((DeclarationsBody) node);

    case CSS_CLASS:
      return appendCssClass((CssClass) node);

    case PSEUDO_CLASS:
      return appendPseudoClass((PseudoClass) node);

    case PSEUDO_ELEMENT:
      return appendPseudoElement((PseudoElement) node);

    case NTH:
      return appendNth((Nth) node);

    case SELECTOR:
      return appendSelector((Selector) node);

    case SIMPLE_SELECTOR:
      return appendSimpleSelector((SimpleSelector) node);

    case SELECTOR_OPERATOR:
      return appendSelectorOperator((SelectorOperator) node);

    case SELECTOR_COMBINATOR:
      return appendSelectorCombinator((SelectorCombinator) node);

    case SELECTOR_ATTRIBUTE:
      return appendSelectorAttribute((SelectorAttribute) node);

    case ID_SELECTOR:
      return appendIdSelector((IdSelector) node);

    case CHARSET_DECLARATION:
      return appendCharsetDeclaration((CharsetDeclaration) node);

    case FONT_FACE:
      return appendFontFace((FontFace) node);

    case NAMED_EXPRESSION:
      return appendNamedExpression((NamedExpression) node);

    case COMPOSED_EXPRESSION:
      return appendComposedExpression((ComposedExpression) node);

    case EXPRESSION_OPERATOR:
      return appendExpressionOperator((ExpressionOperator) node);

    case STRING_EXPRESSION:
      return appendCssString((CssString) node);

    case NUMBER:
      return appendNumberExpression((NumberExpression) node);

    case IDENTIFIER_EXPRESSION:
      return appendIdentifierExpression((IdentifierExpression) node);

    case COLOR_EXPRESSION:
      return appendColorExpression((ColorExpression) node);

    case FUNCTION:
      return appendFunctionExpression((FunctionExpression) node);

    case DECLARATION:
      return appendDeclaration((Declaration) node);

    case MEDIA:
      return appendMedia((Media) node);

    case MEDIUM:
      return appendMedium((Medium) node);

    case STYLE_SHEET:
      return appendStyleSheet((StyleSheet) node);

    default:
      throw new IllegalStateException("Unknown: " + node.getType());
    }
  }

  private boolean appendNth(Nth node) {
    switch (node.getForm()) {
    case EVEN:
      builder.append("even");
      return true;

    case ODD:
      builder.append("odd");
      return true;

    case STANDARD:
      if (node.getRepeater() != null)
        append(node.getRepeater());
      if (node.getMod() != null)
        append(node.getMod());

    }

    return true;
  }

  /**
   * FIXME: DOCUMENT: if the comment after ruleset is not preceded by an empty line, less.js 
   * does not put new line before it. We handle comments differently, a comment is preceded by
   * a new line if it was preceded by it in the input.  
   * 
   * * Input:
   *   p ~ * { background: lime; }
   *   
   *   /* let's try some pseudos that are not valid CSS but are likely to
   *   be implemented as extensions in some UAs. These should not be
   *   recognised, as UAs implementing such extensions should use the
   *   :-vnd-ident syntax. * /
   *   
   * * Less.js output:
   * }
   * /* let's try some pseudos that are not valid CSS but are likely to
   * be implemented as extensions in some UAs. These should not be
   * recognised, as UAs implementing such extensions should use the
   * :-vnd-ident syntax. * /
   *
   * * Less.js output if there would not be an empty line:
   * p ~ * {
   *   background: lime;
   * } /* let's try some pseudos that are not valid CSS but are likely to
   * be implemented as extensions in some UAs. These should not be
   * recognised, as UAs implementing such extensions should use the
   * :-vnd-ident syntax. * /
   * 

   */
  
  private void appendComments(List<Comment> comments, boolean ensureSeparator) {
    if (comments == null || comments.isEmpty())
      return;

    builder.ensureSeparator();

    for (Comment comment : comments) {
      builder.append(comment.getComment());
      if (comment.hasNewLine())
        builder.newLine();
    }

    if (ensureSeparator)
      builder.ensureSeparator();
  }

  // FIXME: does less.js keeps original cases in charsets and elsewhere or not?
  // I'm converting it all to
  // lowercases
  public boolean appendFontFace(FontFace node) {
    builder.append("@font-face {").newLine();
    builder.increaseIndentationLevel();
    appendAllChilds(node);
    builder.decreaseIndentationLevel();
    builder.append("}");

    return true;
  }

  // FIXME: does less.js keeps original cases in charsets and elsewhere or not?
  // I'm converting it all to
  // lowercases
  public boolean appendCharsetDeclaration(CharsetDeclaration node) {
    builder.append("@charset").ensureSeparator();
    builder.append(node.getCharset());
    builder.append(";");

    return true;
  }

  public boolean appendIdSelector(IdSelector node) {
    builder.append("#");
    builder.append(node.getName());

    return true;
  }

  public boolean appendSelectorAttribute(SelectorAttribute node) {
    builder.append("[");
    builder.append(node.getName());
    append(node.getOperator());
    builder.appendIgnoreNull(node.getValue());
    builder.append("]");

    return true;
  }

  private boolean appendSelectorOperator(SelectorOperator operator) {
    switch (operator.getOperator()) {
    case NONE:
      break;

    case EQUALS:
      builder.append("=");
      break;

    case INCLUDES:
      builder.append("~=");
      break;

    case SPECIAL_PREFIX:
      builder.append("|=");
      break;

    case PREFIXMATCH:
      builder.append("^=");
      break;

    case SUFFIXMATCH:
      builder.append("$=");
      break;

    case SUBSTRINGMATCH:
      builder.append("*=");
      break;

    default:
      throw new IllegalStateException("Unknown: " + operator);
    }
    return true;
  }

  public boolean appendPseudoClass(PseudoClass node) {
    builder.append(":");
    builder.append(node.getName());
    if (node.hasParameters()) {
      builder.append("(");
      append(node.getParameter());
      builder.append(")");
    }

    return true;
  }

  public boolean appendPseudoElement(PseudoElement node) {
    builder.append(":");
    if (!node.isLevel12Form())
      builder.append(":");

    builder.append(node.getName());

    return true;
  }

  public boolean appendStyleSheet(StyleSheet styleSheet) {
    appendAllChilds(styleSheet);
    return true;
  }

  // TODO test with empty selector e.g.:
  // 1.) "something, , else"
  // 2.) "something, , ,"
  // 3.) "" - what happen is some rule set has no selector?
  public boolean appendRuleset(RuleSet ruleSet) {
    if (ruleSet.hasEmptyBody())
      return false;

    appendSelectors(ruleSet.getSelectors());
    append(ruleSet.getBody());

    return true;
  }

  public boolean appendDeclarationsBody(DeclarationsBody body) {
    if (body.isEmpty())
      return false;

    builder.ensureSeparator().append("{").newLine();
    builder.increaseIndentationLevel();
    Iterator<Declaration> iterator = body.getDeclarations().iterator();
    while (iterator.hasNext()) {
      Declaration declaration = iterator.next();
      append(declaration);
      builder.ensureNewLine();
    }
    appendComments(body.getOrphanComments(), false);
    builder.decreaseIndentationLevel();
    builder.append("}");

    return true;
  }

  public boolean appendDeclaration(Declaration declaration) {
    builder.appendIgnoreNull(declaration.getName());
    builder.append(":").ensureSeparator();
    if (declaration.getExpression() != null)
      append(declaration.getExpression());
    // FIXME: zdokumentontovat: less.js prints important as it was, e.g. it may
    // not have leading space or it may be ! important <- that is important,
    // because it is one of multiple CSS hacks
    if (declaration.isImportant())
      builder.ensureSeparator().append("!important");
    builder.appendIgnoreNull(";");

    return true;
  }

  public boolean appendMedia(Media node) {
    builder.append("@media");
    appendMedium(node.getMedium());
    builder.ensureSeparator().append("{").newLine();
    builder.increaseIndentationLevel();
    // FIXME: DOCUMENTATION less.js reorders statements in @media. It prints
    // declarations first and rulesets second
    // FIXME: DOCUMENTATION no new line for the last declaration
    // FIXME: DOCUMENTATION media-simple.txt
    Iterator<ASTCssNode> declarations = node.getDeclarations().iterator();
    List<ASTCssNode> ruleSets = node.getRuleSets();
    while (declarations.hasNext()) {
      ASTCssNode body = declarations.next();
      append(body);
      if (declarations.hasNext() || ruleSets.isEmpty())
        builder.ensureNewLine();
    }
    for (ASTCssNode body : ruleSets) {
      append(body);
      builder.ensureNewLine();
    }
    builder.decreaseIndentationLevel();
    builder.append("}");

    return true;
  }

  public boolean appendMedium(Medium medium) {
    Iterator<String> iterator = medium.getMediums().iterator();
    if (!iterator.hasNext())
      return false;
    builder.ensureSeparator().append(iterator.next());
    while (iterator.hasNext()) {
      builder.append(",").ensureSeparator().append(iterator.next());
    }

    return true;
  }

  public boolean appendNamedExpression(NamedExpression expression) {
    builder.append(expression.getName());
    builder.append("=");
    append(expression.getExpression());

    return true;
  }

  public boolean appendComposedExpression(ComposedExpression expression) {
    append(expression.getLeft());
    append(expression.getOperator());
    append(expression.getRight());

    return true;
  }

  public boolean appendExpressionOperator(ExpressionOperator operator) {
    switch (operator.getOperator()) {
    case COMMA:
      builder.append(",").ensureSeparator();
      break;

    case SOLIDUS:
      builder.append("/");
      break;

    case STAR:
      builder.ensureSeparator().append("*");
      break;

    case EMPTY_OPERATOR:
      builder.ensureSeparator();
      break;

    default:
      throw new IllegalStateException("Unknown: " + operator);
    }

    return true;
  }

  public boolean appendCssString(CssString expression) {
    builder.append(expression.getValue());

    return true;
  }

  public boolean appendIdentifierExpression(IdentifierExpression expression) {
    builder.append(expression.getValue());

    return true;
  }

  /**
   * FIXME: DOCUMENT we do not follow color handling in the same way as less.js. If the input
   * contains a color name (red, blue, ...), then we place the color name into the output.
   * 
   * Less.js behaviour is bit more complicated:
   * * if the color name is followed by ;, the less.js preserves the name 
   * * if the color name is NOT followed by ;, the less.js translates color into the code.
   * 
   *  E.g.:
   *  * input: 
   *  li,p { background-color : lime }
   *  li,p { background-color : lime; }
   *  
   *  * less.js output: 
   *  li,
   *  p {
   *    background-color: #00ff00;
   *  }
   *  li,
   *  p {
   *    background-color: lime;
   *  }
   *  
   *  * Our output:
   *  li,
   *  p {
   *    background-color: lime;
   *  }
   *  li,
   *  p {
   *    background-color: lime;
   *  } 
   */
  private boolean appendColorExpression(ColorExpression expression) {
    //if it is named color expression, write out the name
    if (expression instanceof NamedColorExpression) {
      NamedColorExpression named = (NamedColorExpression) expression;
      builder.append(named.getColorName());
    } else {
      builder.append(expression.getValue());
    }

    return true;
  }

  private boolean appendFunctionExpression(FunctionExpression node) {
    builder.append(node.getName());
    builder.append("(");
    append(node.getParameter());
    builder.append(")");

    return true;
  }

  private boolean appendNumberExpression(NumberExpression node) {
    switch (node.getSign()) {
    case PLUS:
      builder.append("+");
      break;

    case MINUS:
      builder.append("-");
      break;
    }

    // FIXME: why does the number contain also the next space????
    builder.append(node.getValueAsString().trim());

    return true;
  }

  public void appendSelectors(List<Selector> selectors) {
    Iterator<Selector> iterator = selectors.iterator();
    while (iterator.hasNext()) {
      Selector selector = iterator.next();
      append(selector);

      if (iterator.hasNext())
        builder.append(",").newLine();
    }
  }

  public boolean appendSelector(Selector selector) {
    if (!selector.isCombined()) {
      append(selector.getHead());
      return true;
    }
    append(selector.getHead());
    append(selector.getCombinator());
    append(selector.getRight());
    return true;
  }

  // TODO add test to all cases
  private boolean appendSimpleSelector(SimpleSelector selector) {
    appendSimpleSelectorHead(selector);
    appendSimpleSelectorTail(selector);
    return true;
  }

  private void appendSimpleSelectorTail(SimpleSelector selector) {
    List<ASTCssNode> allChilds = selector.getSubsequent();
    for (ASTCssNode astCssNode : allChilds) {
      append(astCssNode);
    }
  }

  private boolean appendCssClass(CssClass cssClass) {
    builder.append(".").append(cssClass.getName());
    return true;
  }

  private void appendSimpleSelectorHead(SimpleSelector selector) {
    builder.ensureSeparator();
    if (selector.isStar()) {
      if (!selector.isEmptyForm())
        builder.append("*");
    } else {
      builder.appendIgnoreNull(selector.getElementName());
    }

  }

  // TODO add test on plus greated and empty!!!!!!
  public boolean appendSelectorCombinator(SelectorCombinator combinator) {
    switch (combinator.getCombinator()) {
    case ADJACENT_SIBLING:
      builder.ensureSeparator().append("+");
      break;

    case CHILD:
      builder.ensureSeparator().append(">");
      break;

    case GENERAL_SIBLING:
      builder.ensureSeparator().append("~");
      break;

    case DESCENDANT:
      builder.ensureSeparator();
      break;

    }

    return true;
  }

  private void appendAllChilds(ASTCssNode node) {
    List<? extends ASTCssNode> allChilds = node.getChilds();
    for (ASTCssNode kid : allChilds) {
      if (append(kid))
        builder.ensureNewLine();
    }
  }

}