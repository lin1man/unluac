package unluac.decompile.target;

import unluac.decompile.Decompiler;
import unluac.decompile.Output;
import unluac.decompile.Registers;
import unluac.decompile.Walker;
import unluac.decompile.expression.Expression;
import unluac.decompile.expression.TableReference;

public class TableTarget extends Target {

  private final Registers r;
  private final int line;
  public final Expression table;
  public final Expression index;
  
  public TableTarget(Registers r, int line, Expression table, Expression index) {
    this.r = r;
    this.line = line;
    this.table = table;
    this.index = index;
  }

  @Override
  public void walk(Walker w) {
    table.walk(w);
    index.walk(w);
  }
  
  @Override
  public void print(Decompiler d, Output out, boolean declare) {
    new TableReference(r, line, table, index).print(d, out);
  }
  
  @Override
  public void printMethod(Decompiler d, Output out) {
    table.print(d, out);
    out.print(":");
    out.print(index.asName());
  }
  
  @Override
  public boolean isFunctionName() {
    if(!index.isIdentifier()) {
      return false;
    }
    if(!table.isDotChain()) {
      return false;
    }
    return true;
  }
  
  @Override
  public boolean beginsWithParen() {
    return table.isUngrouped() || table.beginsWithParen();
  }
  
}
