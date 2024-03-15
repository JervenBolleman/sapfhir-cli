/*
 * The MIT License
 *
 * Copyright 2020 Jerven Bolleman <jerven.bolleman@sib.swiss>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package swiss.sib.swissprot.sapfhir.cli;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
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
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.turtle.TurtleWriter;
import org.eclipse.rdf4j.sail.SailException;

import io.github.jervenbolleman.handlegraph4j.gfa1.GFA1Reader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import swiss.sib.swissprot.handlegraph4j.simple.SimplePathGraph;
import swiss.sib.swissprot.handlegraph4j.simple.builders.SimplePathGraphFromGFA1Builder;
import swiss.sib.swissprot.sapfhir.sparql.PathHandleGraphSail;

/**
 * A basic CLI that takes a SPARQL query and tries to translate calls 
 * against the handlegraph4j implementations.
 *
 * @author Jerven Bolleman <jerven.bolleman@sib.swiss>
 */
@Command(name = "sparqlGfa", mixinStandardHelpOptions = true, version = "Run a sparql query against a GFA", description = "Sparql queryies against a GFA")

public class CLI implements Callable<Integer> {

	@Option(names = { "-g", "--gfa" })
	public File gfaFile;

	@Option(names = { "-f", "--byte-buffer" })
	public File byteBuffer;

	@Option(names = { "-b", "--base" }, defaultValue = "http://example.org/vg/")
	public String base;

	@Option(names = { "-t", "--time" }, description = "Time how long the operations take")
	public boolean time = false;

	@Option(names = { "-c", "--convert-to-byte" }, description = "Covert a GFA to handlegraph4j-simple")
	public boolean convert = false;

	@Parameters(index = "0", description = "The SPARQL query to test")
	public String query;

	@Option(names= {"--sync-every"}, defaultValue = "100000")
	private long iterationCacheSyncThreshold = 100_000;
	
	@Override
	public Integer call() throws IOException {
		var startLoad = Instant.now();
		SimplePathGraph spg;
		if (gfaFile != null) {
			spg = loadGFA(gfaFile);
		} else if (byteBuffer != null) {
			spg = loadByteBuffer(byteBuffer);
		} else {
			return 1;
		}
		var endLoad = Instant.now();
		if (time) {

            long loadTime = SECONDS.between(startLoad, endLoad);
            System.err.println("Loaded data in " + loadTime);
        }
		PathHandleGraphSail<?,?,?,?> rep = new PathHandleGraphSail<>(spg, base);
        rep.setIterationCacheSyncThreshold(iterationCacheSyncThreshold);
        int status = convertToByteBuffer(spg);
        if (status != 0)
            return status;
		try {
			SailRepository sr = new SailRepository(rep);
			rep.init();
			try (SailRepositoryConnection connection = sr.getConnection()) {
				Query pTQ = connection.prepareQuery(QueryLanguage.SPARQL, query);
				var startQuery = Instant.now();
				if (pTQ instanceof TupleQuery) {
					SPARQLResultsCSVWriter handler = new SPARQLResultsCSVWriter(System.out);
					((TupleQuery) pTQ).evaluate(handler);
				} else if (pTQ instanceof GraphQuery) {
					RDFHandler createWriter = new TurtleWriter(System.out);
					((GraphQuery) pTQ).evaluate(createWriter);
				} else if (pTQ instanceof BooleanQuery) {
					QueryResultWriter createWriter = QueryResultIO.createWriter(BooleanQueryResultFormat.TEXT,
							System.out);
					boolean evaluate = ((BooleanQuery) pTQ).evaluate();
					createWriter.handleBoolean(evaluate);
				}
				var endQuery = Instant.now();
				if (time) {
					long queryTime = SECONDS.between(startQuery, endQuery);
					System.err.println("Queried in " + queryTime);
				}
			}
            return 0;
        } catch (MalformedQueryException e) {
            System.err.println("Query syntax is broken");
            return 2;
        } catch (SailException | QueryEvaluationException | RDFHandlerException | TupleQueryResultHandlerException
                | RepositoryException e) {
            System.err.println("failed in rdf4j/sapfhir code");
            e.printStackTrace();
            return 1;
        }
    }

	private int convertToByteBuffer(SimplePathGraph spg) throws IOException {
		if (convert) {
			if (gfaFile == null) {
				System.err.println("No gfafile given to load from");
				return 2;
			} else if (byteBuffer == null) {
				System.err.println("No byte buffer code given to load into");
				return 3;
			}
			var startConvert = Instant.now();
			try (FileOutputStream fos = new FileOutputStream(byteBuffer);
					BufferedOutputStream bos = new BufferedOutputStream(fos);
					DataOutputStream out = new DataOutputStream(bos)) {
				spg.writeTo(out);
			}
			var endConvert = Instant.now();
			if (time) {
				long convertTime = SECONDS.between(startConvert, endConvert);
				System.err.println("Converted data in " + convertTime);
			}
		}
		return 0;
	}

	private SimplePathGraph loadByteBuffer(File byteBuffer) throws FileNotFoundException, IOException {
		RandomAccessFile raf = new RandomAccessFile(byteBuffer, "r");
		return SimplePathGraph.open(raf);
	}

	private SimplePathGraph loadGFA(File gfaFile) throws IOException {
		try (Stream<String> lines = Files.lines(gfaFile.toPath(), StandardCharsets.US_ASCII)) {
			SimplePathGraphFromGFA1Builder simplePathGraphFromGFA1Builder = new SimplePathGraphFromGFA1Builder();
			simplePathGraphFromGFA1Builder.parse(new GFA1Reader(lines.iterator()));
			return simplePathGraphFromGFA1Builder.build();
		} catch (Exception e) {
			if (e instanceof IOException) {
				System.err.println("Convertion failed due to io " + e.getMessage());
				throw e;
			} else {
				System.err.println("Convertion failed " + e.getMessage());
				e.printStackTrace();
				throw e;
			}
		}
	}

	public static void main(String[] args) {
		try {
			int exitCode = new CommandLine(new CLI()).execute(args);
			System.exit(exitCode);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
