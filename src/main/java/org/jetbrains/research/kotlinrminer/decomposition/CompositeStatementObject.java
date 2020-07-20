package org.jetbrains.research.kotlinrminer.decomposition;

import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.research.kotlinrminer.diff.CodeRange;
import org.jetbrains.research.kotlinrminer.LocationInfo;
import org.jetbrains.research.kotlinrminer.LocationInfo.*;

import java.util.*;

public class CompositeStatementObject extends AbstractStatement {

    private List<AbstractStatement> statementList;
    private List<AbstractExpression> expressionList;
    private List<VariableDeclaration> variableDeclarations;
    private LocationInfo locationInfo;

    public CompositeStatementObject(KtFile cu, String filePath, KtElement statement, int depth, LocationInfo.CodeElementType codeElementType) {
        super();
        this.setDepth(depth);
        this.locationInfo = new LocationInfo(cu, filePath, statement, codeElementType);
        this.statementList = new ArrayList<>();
        this.expressionList = new ArrayList<>();
        this.variableDeclarations = new ArrayList<>();
    }

    public void addStatement(AbstractStatement statement) {
        statement.setIndex(statementList.size());
        statementList.add(statement);
        statement.setParent(this);
    }

    public List<AbstractStatement> getStatements() {
        return statementList;
    }

    public void addExpression(AbstractExpression expression) {
        //an expression has the same index and depth as the composite statement it belong to
        expression.setDepth(this.getDepth());
        expression.setIndex(this.getIndex());
        expressionList.add(expression);
        expression.setOwner(this);
    }

    public List<AbstractExpression> getExpressions() {
        return expressionList;
    }

    public void addVariableDeclaration(VariableDeclaration declaration) {
        this.variableDeclarations.add(declaration);
    }

    @Override
    public List<StatementObject> getLeaves() {
        List<StatementObject> leaves = new ArrayList<>();
        for (AbstractStatement statement : statementList) {
            leaves.addAll(statement.getLeaves());
        }
        return leaves;
    }

    public List<CompositeStatementObject> getInnerNodes() {
        List<CompositeStatementObject> innerNodes = new ArrayList<>();
        for (AbstractStatement statement : statementList) {
            if (statement instanceof CompositeStatementObject) {
                CompositeStatementObject composite = (CompositeStatementObject) statement;
                innerNodes.addAll(composite.getInnerNodes());
            }
        }
        innerNodes.add(this);
        return innerNodes;
    }

    public boolean contains(AbstractCodeFragment fragment) {
        if (fragment instanceof StatementObject) {
            return getLeaves().contains(fragment);
        } else if (fragment instanceof CompositeStatementObject) {
            return getInnerNodes().contains(fragment);
        } else if (fragment instanceof AbstractExpression) {
            return getExpressions().contains(fragment);
        }
        return false;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(locationInfo.getCodeElementType().getName());
        if (expressionList.size() > 0) {
            sb.append("(");
            for (int i = 0; i < expressionList.size() - 1; i++) {
                sb.append(expressionList.get(i).toString()).append("; ");
            }
            sb.append(expressionList.get(expressionList.size() - 1).toString());
            sb.append(")");
        }
        return sb.toString();
    }

    @Override
    public List<String> getVariables() {
        List<String> variables = new ArrayList<>();
        for (AbstractExpression expression : expressionList) {
            variables.addAll(expression.getVariables());
        }
        return variables;
    }

    @Override
    public List<String> getTypes() {
        List<String> types = new ArrayList<>();
        for (AbstractExpression expression : expressionList) {
            types.addAll(expression.getTypes());
        }
        return types;
    }

    @Override
    public List<VariableDeclaration> getVariableDeclarations() {
        //special handling for enhanced-for formal parameter
        List<VariableDeclaration> variableDeclarations = new ArrayList<>(this.variableDeclarations);
        for (AbstractExpression expression : expressionList) {
            variableDeclarations.addAll(expression.getVariableDeclarations());
        }
        return variableDeclarations;
    }

    public List<String> getAllVariables() {
        List<String> variables = new ArrayList<>(getVariables());
        for (AbstractStatement statement : statementList) {
            if (statement instanceof CompositeStatementObject) {
                CompositeStatementObject composite = (CompositeStatementObject) statement;
                variables.addAll(composite.getAllVariables());
            } else if (statement instanceof StatementObject) {
                StatementObject statementObject = (StatementObject) statement;
                variables.addAll(statementObject.getVariables());
            }
        }
        return variables;
    }

    public List<VariableDeclaration> getAllVariableDeclarations() {
        List<VariableDeclaration> variableDeclarations = new ArrayList<>(getVariableDeclarations());
        for (AbstractStatement statement : statementList) {
            if (statement instanceof CompositeStatementObject) {
                CompositeStatementObject composite = (CompositeStatementObject) statement;
                variableDeclarations.addAll(composite.getAllVariableDeclarations());
            } else if (statement instanceof StatementObject) {
                StatementObject statementObject = (StatementObject) statement;
                variableDeclarations.addAll(statementObject.getVariableDeclarations());
/*                TODO: for(LambdaExpressionObject lambda : statementObject.getLambdas()) {
                    if(lambda.getBody() != null) {
                        variableDeclarations.addAll(lambda.getBody().getAllVariableDeclarations());
                    }
                }*/
            }
        }
        return variableDeclarations;
    }

    public List<VariableDeclaration> getVariableDeclarationsInScope(LocationInfo location) {
        List<VariableDeclaration> variableDeclarations = new ArrayList<>();
        for (VariableDeclaration variableDeclaration : getAllVariableDeclarations()) {
            if (variableDeclaration.getScope().subsumes(location)) {
                variableDeclarations.add(variableDeclaration);
            }
        }
        return variableDeclarations;
    }

    @Override
    public int statementCount() {
        int count = 0;
        if (!this.getString().equals("{"))
            count++;
        for (AbstractStatement statement : statementList) {
            count += statement.statementCount();
        }
        return count;
    }

    public LocationInfo getLocationInfo() {
        return locationInfo;
    }

    public VariableDeclaration getVariableDeclaration(String variableName) {
        List<VariableDeclaration> variableDeclarations = getAllVariableDeclarations();
        for (VariableDeclaration declaration : variableDeclarations) {
            if (declaration.getVariableName().equals(variableName)) {
                return declaration;
            }
        }
        return null;
    }

    public Map<String, Set<String>> aliasedAttributes() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (StatementObject statement : getLeaves()) {
            String s = statement.getString();
            if (s.startsWith("this.") && s.endsWith(";\n")) {
                String firstLine = s.substring(0, s.indexOf("\n"));
                if (firstLine.contains("=")) {
                    String attribute = s.substring(5, s.indexOf("="));
                    String value = s.substring(s.indexOf("=") + 1, s.indexOf(";\n"));
                    if (map.containsKey(value)) {
                        map.get(value).add(attribute);
                    } else {
                        Set<String> set = new LinkedHashSet<>();
                        set.add(attribute);
                        map.put(value, set);
                    }
                }
            }
        }
        Set<String> keysToBeRemoved = new LinkedHashSet<>();
        for (String key : map.keySet()) {
            if (map.get(key).size() <= 1) {
                keysToBeRemoved.add(key);
            }
        }
        for (String key : keysToBeRemoved) {
            map.remove(key);
        }
        return map;
    }

    public CodeRange codeRange() {
        return locationInfo.codeRange();
    }

    public boolean isLoop() {
        return this.locationInfo.getCodeElementType().equals(LocationInfo.CodeElementType.ENHANCED_FOR_STATEMENT) ||
                this.locationInfo.getCodeElementType().equals(CodeElementType.FOR_STATEMENT) ||
                this.locationInfo.getCodeElementType().equals(CodeElementType.WHILE_STATEMENT) ||
                this.locationInfo.getCodeElementType().equals(CodeElementType.DO_STATEMENT);
    }

    public CompositeStatementObject loopWithVariables(String currentElementName, String collectionName) {
        for (CompositeStatementObject innerNode : getInnerNodes()) {
            if (innerNode.getLocationInfo().getCodeElementType().equals(CodeElementType.ENHANCED_FOR_STATEMENT)) {
                boolean currentElementNameMatched = false;
                for (VariableDeclaration declaration : innerNode.getVariableDeclarations()) {
                    if (declaration.getVariableName().equals(currentElementName)) {
                        currentElementNameMatched = true;
                        break;
                    }
                }
                boolean collectionNameMatched = false;
                for (AbstractExpression expression : innerNode.getExpressions()) {
                    if (expression.getVariables().contains(collectionName)) {
                        collectionNameMatched = true;
                        break;
                    }
                }
                if (currentElementNameMatched && collectionNameMatched) {
                    return innerNode;
                }
            } else if (innerNode.getLocationInfo().getCodeElementType().equals(CodeElementType.FOR_STATEMENT) ||
                    innerNode.getLocationInfo().getCodeElementType().equals(CodeElementType.WHILE_STATEMENT)) {
                boolean collectionNameMatched = false;
                for (AbstractExpression expression : innerNode.getExpressions()) {
                    if (expression.getVariables().contains(collectionName)) {
                        collectionNameMatched = true;
                        break;
                    }
                }
                boolean currentElementNameMatched = false;
                for (StatementObject statement : innerNode.getLeaves()) {
                    VariableDeclaration variableDeclaration = statement.getVariableDeclaration(currentElementName);
                    if (variableDeclaration != null && statement.getVariables().contains(collectionName)) {
                        currentElementNameMatched = true;
                        break;
                    }
                }
                if (currentElementNameMatched && collectionNameMatched) {
                    return innerNode;
                }
            }
        }
        return null;
    }
}