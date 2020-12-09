package swiss.sib.swissprot.sapfhir.cli;

import io.github.vgteam.handlegraph4j.gfa1.GFA1Reader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import static java.time.temporal.ChronoUnit.SECONDS;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResultHandlerException;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.QueryResultIO;
import org.eclipse.rdf4j.query.resultio.QueryResultWriter;
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.turtle.TurtleWriter;
import org.eclipse.rdf4j.sail.SailException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import swiss.sib.swissprot.handlegraph4j.simple.SimplePathGraph;
import swiss.sib.swissprot.handlegraph4j.simple.builders.SimplePathGraphFromGFA1Builder;
import swiss.sib.swissprot.sapfhir.sparql.PathHandleGraphSail;

/**
 * A basic CLI that takes a SPARQL query and tries to translate owl:sameAs IRI
 * patterns using current data from the Identifier.org API.
 *
 * @author Jerven Bolleman <jerven.bolleman@sib.swiss>
 */
@Command(name = "sparqlGfa",
        mixinStandardHelpOptions = true,
        version = "Run a sparql query against a GFA",
        description = "Sparql queryies against a GFA")

public class CLI implements Callable<Integer> {

    @Option(names = {"-g", "--gfa"})
    public File gfaFile;

    @Option(names = {"-b", "--base"}, defaultValue = "http://example.org/vg/")
    public String base;

    @Option(names = {"-t", "--time"}, description = "Time how long the operations take")
    public boolean time = false;

    @Parameters(index = "0", description = "The SPARQL query to test")
    public String query;

    @Override
    public Integer call() throws IOException {
        var startLoad = Instant.now();
        SimplePathGraph spg = loadGFA(gfaFile);
        var endLoad = Instant.now();
        if (time) {

            long loadTime = SECONDS.between(startLoad, endLoad);
            System.err.println("Loaded data in " + loadTime);
        }
        var rep = new PathHandleGraphSail<>(spg, base);
        try {
            SailRepository sr = new SailRepository(rep);
            rep.initialize();
            Query pTQ = sr.getConnection().prepareTupleQuery(QueryLanguage.SPARQL, query);
            var startQuery = Instant.now();
            if (pTQ instanceof TupleQuery) {
                SPARQLResultsCSVWriter handler = new SPARQLResultsCSVWriter(System.out);
                ((TupleQuery) pTQ).evaluate(handler);
            } else if (pTQ instanceof GraphQuery) {
                RDFHandler createWriter = new TurtleWriter(System.out);
                ((GraphQuery) pTQ).evaluate(createWriter);
            } else if (pTQ instanceof BooleanQuery) {
                QueryResultWriter createWriter = QueryResultIO.createWriter(BooleanQueryResultFormat.TEXT, System.out);
                boolean evaluate = ((BooleanQuery) pTQ).evaluate();
                createWriter.handleBoolean(evaluate);
            }
            var endQuery = Instant.now();
            if (time) {
                long queryTime = SECONDS.between(startQuery, endQuery);
                System.err.println("Queried in " + queryTime);
            }
            return 0;
        } catch (MalformedQueryException e) {
            System.err.println("Query syntax is broken");
            return 2;
        } catch (SailException | QueryEvaluationException | RDFHandlerException | TupleQueryResultHandlerException
                | RepositoryException e) {
            System.err.println("failed in rdf4j/sapfhir code");
            return 1;
        }
    }

    private SimplePathGraph loadGFA(File gfaFile) throws IOException {
        try ( Stream<String> lines = Files.lines(gfaFile.toPath(), StandardCharsets.US_ASCII)) {
            SimplePathGraphFromGFA1Builder simplePathGraphFromGFA1Builder = new SimplePathGraphFromGFA1Builder();
            simplePathGraphFromGFA1Builder.parse(new GFA1Reader(lines.iterator()));
            return simplePathGraphFromGFA1Builder.build();
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLI()).execute(args);
        System.exit(exitCode);
    }

}
