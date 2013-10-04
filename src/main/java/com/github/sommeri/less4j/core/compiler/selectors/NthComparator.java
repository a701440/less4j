package com.github.sommeri.less4j.core.compiler.selectors;

import com.github.sommeri.less4j.core.ast.Nth;
import com.github.sommeri.less4j.core.ast.NumberExpression;
import com.github.sommeri.less4j.core.compiler.expressions.FormalisticExpressionComparator;
import com.github.sommeri.less4j.core.compiler.expressions.GuardsComparator;
import com.github.sommeri.less4j.core.compiler.expressions.PatternsComparator;

public class NthComparator {
  
  private FormalisticExpressionComparator expressionComparator = new FormalisticExpressionComparator();

  public boolean equals(Nth nth1, Nth nth2) {
    if (nth1.getForm() != nth2.getForm())
      return false;

    switch (nth1.getForm()) {
    case EVEN:
    case ODD:
      return true;

    case STANDARD:
      return equals(nth1.getRepeater(), nth2.getRepeater()) && equals(nth1.getMod(), nth2.getMod());
    default:
      //FIXME (!!!) report error
      return false;
    }
  }

  private boolean equals(NumberExpression v1, NumberExpression v2) {
    if (v1 == null || v2 == null)
      return (v1 == null && v2 == null);

    return expressionComparator.equal(v1, v2);
  }
}
