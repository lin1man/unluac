package unluac.decompile.block;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import unluac.decompile.CloseType;
import unluac.decompile.Walker;
import unluac.decompile.statement.Statement;
import unluac.parse.LFunction;

abstract public class ContainerBlock extends Block {

  protected final List<Statement> statements;
  protected final CloseType closeType;
  protected final int closeLine;
  protected boolean usingClose;
  
  public ContainerBlock(LFunction function, int begin, int end, CloseType closeType, int closeLine, int priority) {
    super(function, begin, end, priority);
    this.closeType = closeType;
    this.closeLine = closeLine;
    this.usingClose = false;
    statements = new ArrayList<Statement>(Math.max(4, end - begin + 1));
  }
  
  @Override
  public void walk(Walker w) {
    w.visitStatement(this);
    for(Statement statement : statements) {
      statement.walk(w);
    }
  }
  
  @Override
  public boolean isContainer() {
    return begin < end;
  }
  
  @Override
  public boolean isEmpty() {
    return statements.isEmpty();
  }
  
  @Override
  public void addStatement(Statement statement) {
    statements.add(statement);
  }
  
  @Override
  public boolean hasCloseLine() {
    return closeType != CloseType.NONE;
  }
  
  @Override
  public int getCloseLine() {
    if(closeType == CloseType.NONE) {
      throw new IllegalStateException();
    }
    return closeLine;
  }
  
  @Override
  public void useClose() {
    usingClose = true;
  }

  public int getStatementSize() {
    return statements.size();
  }
  public Statement getStatement(int index) {
    if (index < 0 || index >= statements.size()) return null;
    return statements.get(index);
  }
  public void setStatement(int index, Statement statement) {
    if (index < 0 || index > statements.size()) return;
    if (statement == null) {
      statements.remove(index);
    } else {
      statements.set(index, statement);
    }
  }
  public void removeStatement(Set<String> sids) {
    if (statements == null || sids.isEmpty()) return;
    statements.removeIf(statement -> statement != null && sids.contains(statement.getSid()));
  }
}
