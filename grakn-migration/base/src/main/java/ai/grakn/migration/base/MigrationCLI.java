/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.migration.base;

import ai.grakn.Grakn;
import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.client.Client;
import ai.grakn.graql.Graql;
import ai.grakn.graql.QueryBuilder;
import ai.grakn.util.Schema;
import com.google.common.io.Files;
import org.apache.commons.cli.HelpFormatter;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.grakn.graql.Graql.count;
import static ai.grakn.graql.Graql.var;
import static java.util.stream.Collectors.joining;

/**
 *
 * @author alexandraorth
 */
public class MigrationCLI {

    private static final String COULD_NOT_CONNECT  = "Could not connect to Grakn Engine. Have you run 'grakn.sh start'?";

    public static <T extends MigrationOptions> List<Optional<T>> init(String[] args, Function<String[], T> constructor) {
        try {
            // get the options from the command line
            T baseOptions = constructor.apply(args);

            // If there is configuration, create multiple options objects from the config
            if (baseOptions.getConfiguration() != null) {
                return extractOptionsFromConfiguration(baseOptions.getConfiguration(), args).stream()
                        .map(constructor)
                        .map(MigrationCLI::validate)
                        .collect(Collectors.toList());
            } else { // Otherwise, create options from the base options
                return Collections.singletonList(validate(baseOptions));
            }
        } catch (Exception e){
            System.err.println(e.getMessage());
        }

        return Collections.emptyList();
    }

    private static <T extends MigrationOptions> Optional<T> validate(T options){
        // Print the help message
        if (options.isHelp()) {
            printHelpMessage(options);
            return Optional.empty();
        }

        // Check that options were provided
        if(options.getNumberOptions() == 0){
            printHelpMessage(options);
            return Optional.empty();
        }

        // Check that engine is running
        if(!Client.serverIsRunning(options.getUri())){
            System.err.println(COULD_NOT_CONNECT);
            return Optional.empty();
        }

        return Optional.of(options);
    }

    public static void loadOrPrint(File templateFile, Stream<Map<String, Object>> data, MigrationOptions options){
        String template = fileAsString(templateFile);
        Migrator migrator = Migrator.to(options.getUri(), options.getKeyspace());

        if(options.isNo()){
            migrator.print(template, data);
        } else {
            printInitMessage(options);
            migrator.load(template, data,
                    options.getBatch(), options.getNumberActiveTasks(), options.getRetry());
            printWholeCompletionMessage(options);
        }
    }

    public static void printInitMessage(MigrationOptions options){
        System.out.println("Migrating data " + (options.hasInput() ? options.getInput() : "") +
                " using Grakn Engine " + options.getUri() +
                " into graph " + options.getKeyspace());
    }

    public static void printWholeCompletionMessage(MigrationOptions options){
        System.out.println("Migration complete.");

        if(options.isVerbose()) {
            System.out.println("Gathering information about migrated data. If in a hurry, you can ctrl+c now.");

            GraknTx graph = Grakn.session(options.getUri(), options.getKeyspace()).open(GraknTxType.WRITE);
            QueryBuilder qb = graph.graql();

            StringBuilder builder = new StringBuilder();
            builder.append("Graph schema contains:\n");
            builder.append("\t ").append(graph.admin().getMetaEntityType().instances().count()).append(" entity types\n");
            builder.append("\t ").append(graph.admin().getMetaRelationType().instances().count()).append(" relation types\n");
            builder.append("\t ").append("0 role types\n");
            builder.append("\t ").append(graph.admin().getMetaResourceType().instances().count()).append(" resource types\n");
            builder.append("\t ").append(graph.admin().getMetaRuleType().instances().count()).append(" rule types\n\n");

            builder.append("Graph data contains:\n");
            builder.append("\t ").append(qb.match(var("x").isa(var("y")), var("y").sub(Graql.label(Schema.MetaSchema.ENTITY.getLabel()))).select("x").distinct().aggregate(count()).execute()).append(" entities\n");
            builder.append("\t ").append(qb.match(var("x").isa(var("y")), var("y").sub(Graql.label(Schema.MetaSchema.RELATIONSHIP.getLabel()))).select("x").distinct().aggregate(count()).execute()).append(" relations\n");
            builder.append("\t ").append(qb.match(var("x").isa(var("y")), var("y").sub(Graql.label(Schema.MetaSchema.ATTRIBUTE.getLabel()))).select("x").distinct().aggregate(count()).execute()).append(" resources\n");
            builder.append("\t ").append(qb.match(var("x").isa(var("y")), var("y").sub(Graql.label(Schema.MetaSchema.RULE.getLabel()))).select("x").distinct().aggregate(count()).execute()).append(" rules\n\n");

            System.out.println(builder);

            graph.close();
        }
    }

    private static String fileAsString(File file){
        try {
            return Files.readLines(file, StandardCharsets.UTF_8).stream().collect(joining("\n"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file " + file.getPath(), e);
        }
    }

    private static void printHelpMessage(MigrationOptions options){
        HelpFormatter helpFormatter = new HelpFormatter();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(System.out, Charset.defaultCharset());
        PrintWriter printWriter = new PrintWriter(new BufferedWriter(outputStreamWriter));
        int width = helpFormatter.getWidth();
        int leftPadding = helpFormatter.getLeftPadding();
        int descPadding = helpFormatter.getDescPadding();
        helpFormatter.printHelp(printWriter, width, "migration.sh", null, options.getOptions(), leftPadding, descPadding, null);
        printWriter.flush();
    }

    private static List<String[]> extractOptionsFromConfiguration(String path, String[] args){
        // check file exists
        File configuration = new File(path);
        if(!configuration.exists()){
            throw new RuntimeException("Could not find configuration file "+ path);
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configuration), Charset.defaultCharset())){
            List<Map<String, String>> config = (List<Map<String, String>>) new Yaml().load(reader);

            List<String[]> options = new ArrayList<>();
            for(Map<String, String> c:config){

                List<String> parameters = new ArrayList<>(Arrays.asList(args));

                c.entrySet().stream()
                        .flatMap(m -> Stream.of("-" + m.getKey(), m.getValue()))
                        .forEach(parameters::add);

                options.add(parameters.toArray(new String[parameters.size()]));
            }

            return options;
        } catch (IOException e){
            throw new RuntimeException("Could not parse configuration file.");
        }
    }
}
