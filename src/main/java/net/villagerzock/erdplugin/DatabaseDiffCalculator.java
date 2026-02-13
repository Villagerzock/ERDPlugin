package net.villagerzock.erdplugin;

import com.intellij.database.model.DasNamespace;
import com.intellij.database.psi.DbNamespaceImpl;
import net.villagerzock.erdplugin.node.Attribute;
import net.villagerzock.erdplugin.node.Node;
import net.villagerzock.erdplugin.node.NodeGraph;
import org.bouncycastle.jcajce.provider.asymmetric.rsa.CipherSpi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseDiffCalculator {
    public static String calculateDiffScript(String databaseName, NodeGraph graph){
        StringBuilder builder = new StringBuilder();
        List<String> stmts = calculateDiffStatements(databaseName,graph);
        for (String s : stmts){
            builder.append(s);
            builder.append("\n");
        }
        return builder.toString();
    }

    public static List<String> calculateDiffStatements(String databaseName, NodeGraph graph){
        String drop = "DROP DATABASE IF EXISTS `%s`;";
        String create = "CREATE DATABASE `%s`;";
        String use = "USE `%s`;";

        List<String> result = new ArrayList<>();

        result.add(String.format(drop,databaseName));
        result.add(String.format(create,databaseName));
        result.add(String.format(use,databaseName));

        String createTable = """
                CREATE TABLE IF NOT EXISTS `%s`
                (
                \t%s
                \t%s
                \t%s
                );
                """;

        for (Node node : graph.nodes()){
            StringBuilder attrBuilder = new StringBuilder();
            List<String> pks = new ArrayList<>();
            List<Attribute> attributes = node.getAttributes().values().stream().toList();
            for (int i = 0; i<attributes.size(); i++){
                Attribute attribute = attributes.get(i);
                attrBuilder.append("`");
                attrBuilder.append(attribute.name());
                attrBuilder.append("` ");
                attrBuilder.append(attribute.type());
                if (attribute.primaryKey()){
                    pks.add(attribute.name());
                    attrBuilder.append(" NOT NULL");
                    if (attribute.autoIncrement()){
                        attrBuilder.append(" AUTO_INCREMENT");
                    }
                }else {
                    attrBuilder.append(attribute.nullable() ? " NULL" : " NOT NULL");
                }
                if (i<node.getAttributes().size()-1 || !pks.isEmpty()){
                    attrBuilder.append(",\n\t");
                }
            }
            StringBuilder pkBuilder = new StringBuilder();
            if (!pks.isEmpty()){
                pkBuilder.append("PRIMARY KEY(`");
                for (int i = 0; i<pks.size(); i++){
                    pkBuilder.append(pks.get(i));
                    if (i<pks.size()-1){
                        pkBuilder.append("`, `");
                    }
                }
                pkBuilder.append("`)");
            }
            String formatted = String.format(createTable,node.getName(),attrBuilder,pkBuilder,"");
            result.add(formatted);
        }

        String alterTable = """
                ALTER TABLE `%s`
                \tADD CONSTRAINT `%s`
                \tFOREIGN KEY (`%s`)
                \tREFERENCES `%s`(`%s`);
                """;


        return result;
    }
}
