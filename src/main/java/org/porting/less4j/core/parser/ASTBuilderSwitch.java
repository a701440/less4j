package org.porting.less4j.core.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.runtime.Token;
import org.porting.less4j.core.ast.ASTCssNode;
import org.porting.less4j.core.ast.ASTCssNodeType;
import org.porting.less4j.core.ast.CharsetDeclaration;
import org.porting.less4j.core.ast.Comment;
import org.porting.less4j.core.ast.ComposedExpression;
import org.porting.less4j.core.ast.CssClass;
import org.porting.less4j.core.ast.Declaration;
import org.porting.less4j.core.ast.DeclarationsBody;
import org.porting.less4j.core.ast.Expression;
import org.porting.less4j.core.ast.ExpressionOperator;
import org.porting.less4j.core.ast.FontFace;
import org.porting.less4j.core.ast.IdSelector;
import org.porting.less4j.core.ast.IdentifierExpression;
import org.porting.less4j.core.ast.Media;
import org.porting.less4j.core.ast.Medium;
import org.porting.less4j.core.ast.Nth;
import org.porting.less4j.core.ast.Nth.Form;
import org.porting.less4j.core.ast.NumberExpression;
import org.porting.less4j.core.ast.Pseudo;
import org.porting.less4j.core.ast.PseudoClass;
import org.porting.less4j.core.ast.PseudoElement;
import org.porting.less4j.core.ast.RuleSet;
import org.porting.less4j.core.ast.Selector;
import org.porting.less4j.core.ast.SelectorAttribute;
import org.porting.less4j.core.ast.SelectorOperator;
import org.porting.less4j.core.ast.StyleSheet;

class ASTBuilderSwitch extends TokenTypeSwitch<ASTCssNode> {

  private final TermBuilder termBuilder = new TermBuilder(this);
  // as stated here: http://www.w3.org/TR/css3-selectors/#pseudo-elements
  private static Set<String> COLONLESS_PSEUDOELEMENTS = new HashSet<String>();
  static {
    COLONLESS_PSEUDOELEMENTS.add("first-line");
    COLONLESS_PSEUDOELEMENTS.add("first-letter");
    COLONLESS_PSEUDOELEMENTS.add("before");
    COLONLESS_PSEUDOELEMENTS.add("after");
  }

  //FIXME: this system that require postprocess everywhere is extremely bad. Desperately needs refactoring.
  @Override
  public <M extends ASTCssNode> M postprocess(M something) {
    HiddenTokenAwareTree underlyingStructure = something.getUnderlyingStructure();
    List<Comment> preceding = convertToComments(underlyingStructure.getPreceding());
    something.setOpeningComments(preceding);

    List<Comment> following = convertToComments(underlyingStructure.getFollowing());
    something.setTrailingComments(following);

    List<Comment> orphans = convertToComments(underlyingStructure.getOrphans());
    something.setOrphanComments(orphans);
    return something;
  }

  private List<Comment> convertToComments(List<Token> preceding) {
    List<Comment> result = new ArrayList<Comment>();

    Comment comment = null;
    for (Token token : preceding) {
      if (token.getType() == LessLexer.COMMENT) {
        comment = new Comment(new HiddenTokenAwareTree(token));
        result.add(comment);
      }
      if (token.getType() == LessLexer.NEW_LINE) {
        if (comment != null)
          comment.setHasNewLine(true);
      }
    }

    // FIXME DOCUMENT: I do not follow less.js /* A C-style comment */\n\n I
    // will create 1 new line less 2 new lines

    return result;
  }

  public StyleSheet handleStyleSheet(HiddenTokenAwareTree token) {
    StyleSheet result = new StyleSheet(token);
    for (HiddenTokenAwareTree kid : token.getChildren()) {
      result.addChild(switchOn(kid));
    }

    return result;
  }

  public Expression handleTerm(HiddenTokenAwareTree token) {
    return termBuilder.buildFromTerm(token);
  }

  public Expression handleExpression(HiddenTokenAwareTree token) {
    List<HiddenTokenAwareTree> children = token.getChildren();
    if (children.size() == 0)
      throw new IncorrectTreeException();

    if (children.size() == 1) {
      Expression head = (Expression) switchOn(children.get(0));
      // we have to switch to parent token, because we would loose it otherwise.
      // for example, comments before simple expressions would not be accessible
      // anymore
      head.setUnderlyingStructure(token);
      return head;
    }

    return createExpression(token, children);
  }

  private Expression createExpression(HiddenTokenAwareTree parent, List<HiddenTokenAwareTree> members) {
    // this must represent a term. Otherwise we are doomed anyway.
    Expression head = (Expression) switchOn(members.get(0));
    if (members.size() == 1) {
      return head;
    }

    HiddenTokenAwareTree token = members.get(1);
    ExpressionOperator operator = postprocess(new ExpressionOperator(token, toExpressionOperator(token)));

    if (members.size() < 2)
      return new ComposedExpression(parent, head, operator, null);

    return new ComposedExpression(parent, head, operator, createExpression(parent, members.subList(2, members.size())));
  }

  private ExpressionOperator.Operator toExpressionOperator(HiddenTokenAwareTree token) {
    switch (token.getType()) {
    case LessLexer.SOLIDUS:
      return ExpressionOperator.Operator.SOLIDUS;

    case LessLexer.COMMA:
      return ExpressionOperator.Operator.COMMA;

    case LessLexer.STAR:
      return ExpressionOperator.Operator.STAR;

    case LessLexer.EMPTY_SEPARATOR:
      return ExpressionOperator.Operator.EMPTY_OPERATOR;

    default:
      break;
    }

    throw new IncorrectTreeException();
  }

  public Declaration handleDeclaration(HiddenTokenAwareTree token) {
    List<HiddenTokenAwareTree> children = token.getChildren();

    String name = children.get(0).getText();
    if (children.size() == 1)
      return new Declaration(token, name);

    HiddenTokenAwareTree expressionToken = children.get(1);
    if (expressionToken.getType() == LessLexer.IMPORTANT_SYM)
      return new Declaration(token, name, null, true);

    Expression expression = (Expression) switchOn(expressionToken);
    if (children.size() == 2)
      return new Declaration(token, name, expression);

    if (children.get(2).getType() == LessLexer.IMPORTANT_SYM)
      return new Declaration(token, name, expression, true);

    throw new IncorrectTreeException();
  }

  public FontFace handleFontFace(HiddenTokenAwareTree token) {
    FontFace result = new FontFace(token);

    List<HiddenTokenAwareTree> children = token.getChildren();
    List<Declaration> declarations = new ArrayList<Declaration>();
    for (HiddenTokenAwareTree kid : children) {
      if (kid.getType() == LessLexer.DECLARATION)
        declarations.add(postprocess(handleDeclaration(kid)));
    }

    result.addDeclarations(declarations);
    return result;
  }

  public CharsetDeclaration handleCharsetDeclaration(HiddenTokenAwareTree token) {
    // FIXME: just an idea, what does less.js do if charset is followed by a lot
    // of declarations?
    List<HiddenTokenAwareTree> children = token.getChildren();
    if (children.isEmpty())
      throw new IncorrectTreeException();

    return new CharsetDeclaration(token, children.get(0).getText());
  }

  public RuleSet handleRuleSet(HiddenTokenAwareTree token) {
    RuleSet ruleSet = new RuleSet(token);

    List<Selector> selectors = new ArrayList<Selector>();
    List<HiddenTokenAwareTree> children = token.getChildren();

    ASTCssNode previousKid = null;
    for (HiddenTokenAwareTree kid : children) {
      if (kid.getType() == LessLexer.SELECTOR) {
        Selector selector = postprocess(handleSelector(kid));
        selectors.add(selector);
        previousKid = selector;
      }
      if (kid.getType() == LessLexer.BODY_OF_DECLARATIONS) {
        DeclarationsBody body = postprocess(handleDeclarationsBody(kid));
        ruleSet.setBody(body);
        previousKid = body;
      }
      if (kid.getType() == LessLexer.COMMA) {
        List<Comment> comments = convertToComments(kid.getPreceding());
        previousKid.addTrailingComments(comments);
      }
    }

    ruleSet.addSelectors(selectors);
    return ruleSet;
  }

  public DeclarationsBody handleDeclarationsBody(HiddenTokenAwareTree token) {
    if (token.getChildren() == null)
      return new DeclarationsBody(token);

    List<Declaration> declarations = new ArrayList<Declaration>();
    for (HiddenTokenAwareTree kid : token.getChildren()) {
      if (kid.getType() == LessLexer.DECLARATION)
        declarations.add(postprocess(handleDeclaration(kid)));
    }

    return new DeclarationsBody(token, declarations);
  }

  public Selector handleSelector(HiddenTokenAwareTree token) {
    SelectorBuilder builder = new SelectorBuilder(token, this);
    return builder.buildSelector();
  }

  public CssClass handleCssClass(HiddenTokenAwareTree token) {
    List<HiddenTokenAwareTree> children = token.getChildren();
    HiddenTokenAwareTree nameToken = children.get(0);

    String name = nameToken.getText();
    if (nameToken.getType()!=LessLexer.IDENT && name.length()>1) {
      name=name.substring(1, name.length());
    }
    
    CssClass result = new CssClass(token, name);
    return result;
  }

  public SelectorAttribute handleSelectorAttribute(HiddenTokenAwareTree token) {
    List<HiddenTokenAwareTree> children = token.getChildren();
    if (children.size() == 0)
      throw new IncorrectTreeException();

    if (children.size() == 1)
      return new SelectorAttribute(token, children.get(0).getText());

    if (children.size() < 3)
      throw new IncorrectTreeException();

    return new SelectorAttribute(token, children.get(0).getText(), postprocess(handleSelectorOperator(children.get(1))), children.get(2).getText());
  }

  public SelectorOperator handleSelectorOperator(HiddenTokenAwareTree token) {
    return new SelectorOperator(token, toSelectorOperator(token));
  }

  private SelectorOperator.Operator toSelectorOperator(HiddenTokenAwareTree token) {
    switch (token.getType()) {
    case LessLexer.OPEQ:
      return SelectorOperator.Operator.EQUALS;

    case LessLexer.INCLUDES:
      return SelectorOperator.Operator.INCLUDES;

    case LessLexer.DASHMATCH:
      return SelectorOperator.Operator.SPECIAL_PREFIX;

    case LessLexer.PREFIXMATCH:
      return SelectorOperator.Operator.PREFIXMATCH;

    case LessLexer.SUFFIXMATCH:
      return SelectorOperator.Operator.SUFFIXMATCH;

    case LessLexer.SUBSTRINGMATCH:
      return SelectorOperator.Operator.SUBSTRINGMATCH;

    default:
      break;
    }

    throw new IncorrectTreeException();
  }

  public Pseudo handlePseudo(HiddenTokenAwareTree token) {
    List<HiddenTokenAwareTree> children = token.getChildren();
    if (children.size() == 0 || children.size() == 1)
      throw new IncorrectTreeException();

    // the child number 0 is a :
    HiddenTokenAwareTree t = children.get(1);
    if (t.getType() == LessLexer.COLON) {
      return createPseudoElement(token, 2, false);
    }

    if (COLONLESS_PSEUDOELEMENTS.contains(t.getText().toLowerCase())) {
      return createPseudoElement(token, 1, true);
    }

    if (children.size() == 2)
      return new PseudoClass(token, children.get(1).getText());

    // TODO: still ugly solution, but works for basic cases
    if (children.size() == 3) {
      HiddenTokenAwareTree parameter = children.get(2);
      if (parameter.getType() == LessLexer.NUMBER)
        return new PseudoClass(token, children.get(1).getText(), new NumberExpression(parameter, parameter.getText()));
      if (parameter.getType() == LessLexer.IDENT)
        return new PseudoClass(token, children.get(1).getText(), new IdentifierExpression(parameter, parameter.getText()));

      // FIXME: add test for all possible variants of an+b and cases
      return new PseudoClass(token, children.get(1).getText(), switchOn(parameter));
    }

    // FIXME: handle this case the same way as less.js
    throw new IncorrectTreeException();
  }

  public Nth handleNth(HiddenTokenAwareTree token) {
    Expression first = null;
    Expression second = null;
    if (hasChildren(token.getChild(0))) {
      first = termBuilder.buildFromTerm(token.getChild(0));
      if (first.getType() == ASTCssNodeType.IDENTIFIER_EXPRESSION) {
        IdentifierExpression ident = (IdentifierExpression) first;
        if ("even".equals(ident.getValue().toLowerCase())) {
          return new Nth(token, null, null, Form.EVEN);
        } else if ("odd".equals(ident.getValue().toLowerCase())) {
          return new Nth(token, null, null, Form.ODD);
        } else if ("n".equals(ident.getValue().toLowerCase())) {
          first = new NumberExpression(token.getChild(0), "n", NumberExpression.Sign.NONE, NumberExpression.Dimension.REPEATER);
        } else if ("-n".equals(ident.getValue().toLowerCase())) {
          first = new NumberExpression(token.getChild(0), "n", NumberExpression.Sign.MINUS, NumberExpression.Dimension.REPEATER);
        } else
          throw new IllegalStateException("Unexpected identifier value for nth: " + ident.getValue());
      }
    }

    if (token.getChild(1) != null && hasChildren(token.getChild(1))) {
      second = termBuilder.buildFromTerm(token.getChild(1));
    }

    return new Nth(token, (NumberExpression) first, (NumberExpression) second);
  }

  private boolean hasChildren(HiddenTokenAwareTree token) {
    return token.getChildren() != null && !token.getChildren().isEmpty();
  }

  private PseudoElement createPseudoElement(HiddenTokenAwareTree token, int startIndex, boolean level12Form) {
    List<HiddenTokenAwareTree> children = token.getChildren();
    String name = children.get(startIndex).getText();
    return new PseudoElement(token, name, level12Form);
  }

  public IdSelector handleIdSelector(HiddenTokenAwareTree token) {
    List<HiddenTokenAwareTree> children = token.getChildren();
    if (children.size() != 1)
      throw new IncorrectTreeException();

    String text = children.get(0).getText();
    if (text == null || text.length() < 1)
      throw new IncorrectTreeException();

    return new IdSelector(token, text.substring(1));
  }

  public Media handleMedia(HiddenTokenAwareTree token) {
    List<HiddenTokenAwareTree> children = token.getChildren();
    Media result = new Media(token);
    for (HiddenTokenAwareTree kid : children) {
      result.addChild(switchOn(kid));

    }
    return result;
  }

  public Medium handleMediumDeclaration(HiddenTokenAwareTree token) {
    Medium result = new Medium(token);
    List<HiddenTokenAwareTree> children = token.getChildren();
    for (HiddenTokenAwareTree kid : children) {
      result.addMedium(kid.getText());
    }

    return result;
  }

}

// FIXME: do something meaningful instead of this exception
@SuppressWarnings("serial")
class IncorrectTreeException extends RuntimeException {

  public IncorrectTreeException() {
  }

  public IncorrectTreeException(String message) {
    super(message);
  }

}