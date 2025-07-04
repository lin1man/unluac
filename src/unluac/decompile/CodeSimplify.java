package unluac.decompile;

import unluac.decompile.block.Block;
import unluac.decompile.block.ContainerBlock;
import unluac.decompile.expression.*;
import unluac.decompile.statement.Assignment;
import unluac.decompile.statement.FunctionCallStatement;
import unluac.decompile.statement.Return;
import unluac.decompile.statement.Statement;
import unluac.decompile.target.TableTarget;
import unluac.decompile.target.Target;
import unluac.decompile.target.VariableTarget;

import java.util.*;

public class CodeSimplify {
    public static void simplify(Block block, Registers registers) {
        localAssignOpt(block, null);
        removeUnusedLocalVariable(block, false);
    }
    public static AbstractMap.SimpleEntry<Declaration, Expression> getAssignValue(Statement statement) {
        if (!(statement instanceof Assignment)) return null;
        if (((Assignment) statement).getArity() != 1) return null;

        Target firstTarget = ((Assignment) statement).getFirstTarget();
        if (!(firstTarget instanceof VariableTarget)) return null;

        return new AbstractMap.SimpleEntry<>(
                ((VariableTarget) firstTarget).decl,
                ((Assignment) statement).getFirstValue()
        );
    }
    public static Expression getLocalVariable(Expression expression, Map<Declaration, Expression> declarations) {
        if (expression instanceof LocalVariable) {
            Declaration decl = ((LocalVariable) expression).decl;
            if (declarations.containsKey(decl)) {
                return declarations.get(decl);
            }
        }
        return null;
    }
    public static FunctionCall replaceFuncCallExpressionValues(Expression expression, Map<Declaration, Expression> declarations) {
        boolean isReplace = false;
        if (expression instanceof FunctionCall) {
            Expression func = ((FunctionCall) expression).function;
            Expression[] args = ((FunctionCall) expression).arguments;
            Expression newFunc = getLocalVariable(func, declarations);
            if (newFunc != null) {
                func = newFunc;
                isReplace = true;
            }
            for (int i = 0; i < args.length; i++) {
                Expression rarg = getLocalVariable(args[i], declarations);
                if (rarg != null) {
                    args[i] = rarg;
                    isReplace = true;
                }
            }
            if (isReplace) {
                return new FunctionCall(func, args, expression.isMultiple());
            }
        }
        return null;
    }
    public static void replaceLocalValues(Statement statement, Map<Declaration, Expression> declarations) {
        if (statement instanceof Assignment) {
            int valueCount = ((Assignment) statement).getTargetArity();
            for (int i = 0; i < valueCount; i++) {
                Expression expression = ((Assignment) statement).getTargetValue(i);
                Expression rexpression = replaceFuncCallExpressionValues(expression, declarations);
                if (rexpression != null) {
                    ((Assignment) statement).replaceTargetValue(i, rexpression);
                    continue;
                }
                if (expression instanceof LocalVariable) {
                    Declaration decl = ((LocalVariable) expression).decl;
                    if (declarations.containsKey(decl)) {
                        ((Assignment) statement).replaceTargetValue(i, declarations.get(decl));
                    }
                }
            }
        } else if (statement instanceof Return) {
            for (int i = 0; i < ((Return) statement).values.length; i++) {
                Expression expression = ((Return) statement).values[i];
                Expression rexpression = replaceFuncCallExpressionValues(expression, declarations);
                if (rexpression != null) {
                    ((Return) statement).values[i] = rexpression;
                }
            }
        } else if (statement instanceof FunctionCallStatement) {
            Expression expression = ((FunctionCallStatement) statement).call;
            FunctionCall rexpression = replaceFuncCallExpressionValues(expression, declarations);
            if (rexpression != null) {
                ((FunctionCallStatement) statement).call = rexpression;
            } else {
                System.out.println("funccall null");
            }
        }
    }
    public static void replaceBinaryValue(Expression expression, Map<Declaration, Expression> declarations) {
        if (!(expression instanceof BinaryExpression)) return;
        Expression le = getLocalVariable(((BinaryExpression) expression).left, declarations);
        if (le != null) {
            ((BinaryExpression) expression).left = le;
        }
        if (((BinaryExpression) expression).right instanceof BinaryExpression) {
            replaceBinaryValue(((BinaryExpression) expression).right, declarations);
        } else {
            Expression re = getLocalVariable(((BinaryExpression) expression).right, declarations);
            if (re != null) {
                ((BinaryExpression) expression).right = re;
            }
        }
    }
    public static void replaceTableReference(Expression expression, Map<Declaration, Expression> declarations) {
        if (!(expression instanceof TableReference)) return;
        Expression table = getLocalVariable(((TableReference) expression).table, declarations);
        if (table != null) {
            if (!(table instanceof TableLiteral)) {
                ((TableReference) expression).table = table;
            }
        }
        Expression index = getLocalVariable(((TableReference) expression).index, declarations);
        if (index != null) {
            ((TableReference) expression).index = index;
        }
    }
    public static boolean handleConstTableLiteral(Statement statement, Map<Declaration, Expression> declarations) {
        if (!(statement instanceof Assignment)) return false;
        if (((Assignment) statement).getArity() != 1) return false;
        Target target = ((Assignment) statement).getTarget(0);
        if (!(target instanceof TableTarget)) return false;
        Expression table = ((TableTarget) target).table;
        Expression ltable = getLocalVariable(table, declarations);
        if (!(ltable instanceof TableLiteral)) return false;
        Expression value = ((Assignment) statement).getTargetValue(0);
        if (!(value instanceof ConstantExpression)
                && !(value instanceof TableLiteral)
                && !(value instanceof UpvalueExpression)) {
            return false;
        }
        ((TableLiteral) ltable).putEntry(((TableTarget) target).index, value);
        return true;
    }
    public static void localAssignOpt(Block block, Map<Declaration, Expression> declarations) {
        Set<String> unusedSid = new HashSet<>();
        if (declarations == null) {
            declarations = new HashMap<>();
        }
        if (!(block instanceof ContainerBlock)) return;
        int statementCount = ((ContainerBlock) block).getStatementSize();
        for (int i = 0; i < statementCount; i++) {
            Statement statement = ((ContainerBlock) block).getStatement(i);
            if (statement instanceof ContainerBlock) {
                localAssignOpt((Block)statement, declarations);
                removeUnusedLocalVariable((Block)statement, true);
                continue;
            }
            replaceLocalValues(statement, declarations);

            AbstractMap.SimpleEntry<Declaration, Expression> entry = getAssignValue(statement);
            if (entry != null) {
                Declaration decl = entry.getKey();
                Expression value = entry.getValue();
                if (value instanceof ClosureExpression) {
                    declarations.remove(decl);// function Lxxx_yyy()
                    continue;
                }
                replaceBinaryValue(value, declarations);
                replaceTableReference(value, declarations);
//                value = replaceExpressionValues(value, declarations);

                if (declarations.containsKey(decl)) {
                    declarations.replace(decl, value);
//                    System.out.println("replace val:" + decl.name);
                } else {
                    declarations.put(decl, value);
                }
            } else {
                if (statement instanceof Assignment) {
                    int arity = ((Assignment) statement).getArity();
                    if (arity != 1) {
                        for (int j = 0; j < arity; j++) {
                            Target target = ((Assignment) statement).getTarget(j);
                            if (target instanceof VariableTarget) {
                                declarations.remove(((VariableTarget) target).decl);
                            }
                        }
                    } else {
                        boolean isTable = handleConstTableLiteral(statement, declarations);
                        if (isTable) {
                            unusedSid.add(statement.getSid());
                            continue;
                        }
                    }
                }
            }
        }
        ((ContainerBlock) block).removeStatement(unusedSid);
    }
    private static class DeclarationInfo {
        public String sid;
        public int count;
        public DeclarationInfo(int count, String sid) {
            this.count = count;
            this.sid = sid;
        }
    }
    public static void removeUnusedLocalVariable(Block block, boolean isSub) {
        Set<String> unusedSid = new HashSet<>();
        Map<Declaration, DeclarationInfo> declarationInfoMap = new HashMap<>();
        if (!(block instanceof ContainerBlock)) return;
        int statementCount = ((ContainerBlock) block).getStatementSize();
        for (int i = 0; i < statementCount; i++) {
            Statement statement = ((ContainerBlock) block).getStatement(i);
            try {
                statement.walk(new Walker() {
                    @Override
                    public void visitExpression(Expression expression) {
                        if (expression instanceof LocalVariable) {
                            Declaration decl = ((LocalVariable) expression).decl;
                            if (declarationInfoMap.containsKey(decl)) {
                                declarationInfoMap.get(decl).count += 1;
                            } else {
//                                System.out.printf("undeclaration val:%s\n", decl.name);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                System.out.printf("statement walk exception:%s\n", e.toString());
            }

            if (!(statement instanceof Assignment)) continue;
            int targetCount = ((Assignment) statement).getArity();
            for (int j = 0; j < targetCount; j++) {
                Target target = ((Assignment) statement).getTarget(j);
                if (!(target instanceof VariableTarget)) continue;
                Declaration decl = ((VariableTarget) target).decl;
                if (declarationInfoMap.containsKey(decl)) {
                    DeclarationInfo info = declarationInfoMap.get(decl);
                    if (info.count == 0) {
                        unusedSid.add(info.sid);
                    }
                    if (targetCount == 1) {
                        declarationInfoMap.replace(decl, new DeclarationInfo(0, statement.getSid()));
                    } else {
                        declarationInfoMap.remove(decl);
                    }
                } else if (targetCount == 1) {
                    declarationInfoMap.put(decl, new DeclarationInfo(0, statement.getSid()));
                }
            }
        }
        if (!isSub) {
            if (block.function.functions == null || block.function.functions.length == 0) {
                for (DeclarationInfo value : declarationInfoMap.values()) {
                    if (value.count == 0) {
                        unusedSid.add(value.sid);
                    }
                }
            }
        }
        ((ContainerBlock) block).removeStatement(unusedSid);
    }
}
