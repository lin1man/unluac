package unluac.decompile;

import javafx.util.Pair;
import unluac.decompile.block.Block;
import unluac.decompile.block.ContainerBlock;
import unluac.decompile.expression.*;
import unluac.decompile.statement.Assignment;
import unluac.decompile.statement.FunctionCallStatement;
import unluac.decompile.statement.Return;
import unluac.decompile.statement.Statement;
import unluac.decompile.target.Target;
import unluac.decompile.target.VariableTarget;

import java.util.*;

public class CodeSimplify {
    public static void simplify(Block block, Registers registers) {
        localAssignOpt(block, registers);
        removeUnusedLocalVariable(block, registers);
    }
    public static Pair<Declaration, Expression> getAssignValue(Statement statement) {
        if (!(statement instanceof Assignment)) return null;
        if (((Assignment) statement).getArity() != 1) return null;

        Target firstTarget = ((Assignment) statement).getFirstTarget();
        if (!(firstTarget instanceof VariableTarget)) return null;

        return new Pair<>(
                ((VariableTarget) firstTarget).decl,
                ((Assignment) statement).getFirstValue()
        );
    }
    public static Expression replaceLocalVariable(Expression expression, Map<Declaration, Expression> declarations) {
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
            Expression newFunc = replaceLocalVariable(func, declarations);
            if (newFunc != null) {
                func = newFunc;
                isReplace = true;
            }
            for (int i = 0; i < args.length; i++) {
                Expression rarg = replaceLocalVariable(args[i], declarations);
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
            }
        }
    }
    public static void replaceBinaryValue(Expression expression, Map<Declaration, Expression> declarations) {
        if (!(expression instanceof BinaryExpression)) return;
        Expression le = replaceLocalVariable(((BinaryExpression) expression).left, declarations);
        if (le != null) {
            ((BinaryExpression) expression).left = le;
        }
        if (((BinaryExpression) expression).right instanceof BinaryExpression) {
            replaceBinaryValue(((BinaryExpression) expression).right, declarations);
        } else {
            Expression re = replaceLocalVariable(((BinaryExpression) expression).right, declarations);
            if (re != null) {
                ((BinaryExpression) expression).right = re;
            }
        }
    }
    public static void localAssignOpt(Block block, Registers registers) {
        Map<Declaration, Expression> declarations = new HashMap<>();
        if (!(block instanceof ContainerBlock)) return;
        int statementCount = ((ContainerBlock) block).getStatementSize();
        for (int i = 0; i < statementCount; i++) {
            Statement statement = ((ContainerBlock) block).getStatement(i);
            replaceLocalValues(statement, declarations);

            Pair<Declaration, Expression> pair = getAssignValue(statement);
            if (pair != null) {
                Declaration decl = pair.getKey();
                Expression value = pair.getValue();
                if (value instanceof ClosureExpression) {
                    declarations.remove(decl);// function Lxxx_yyy()
                    continue;
                }
                replaceBinaryValue(value, declarations);
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
                    }
                }
            }
        }
    }
    private static class DeclarationInfo {
        public String sid;
        public int count;
        public DeclarationInfo(int count, String sid) {
            this.count = count;
            this.sid = sid;
        }
    }
    public static void removeUnusedLocalVariable(Block block, Registers registers) {
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
        if (block.function.functions == null || block.function.functions.length == 0) {
            for (DeclarationInfo value : declarationInfoMap.values()) {
                if (value.count == 0) {
                    unusedSid.add(value.sid);
                }
            }
        }
        ((ContainerBlock) block).removeStatement(unusedSid);
    }
}
