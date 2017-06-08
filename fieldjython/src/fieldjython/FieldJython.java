package fieldjython;

import field.utility.*;
import fieldbox.boxes.Box;
import fieldbox.boxes.Boxes;
import fieldbox.boxes.Drawing;
import fieldbox.boxes.Mouse;
import fieldbox.boxes.plugins.IsExecuting;
import fieldbox.execution.Completion;
import fieldbox.execution.Execution;
import fieldbox.execution.JavaSupport;
import fieldbox.io.IO;
import fielded.Animatable;
import fielded.TextUtils;
import fielded.plugins.Out;
import fieldnashorn.TernSupport;
import org.python.core.Py;
import org.python.core.PyFunction;
import org.python.core.PyObject;
import org.python.google.common.collect.MapMaker;
import org.python.util.PythonInterpreter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static field.utility.Log.log;

/**
 * Created by marc on 10/23/14.
 */
public class FieldJython extends Execution implements IO.Loaded {

	Lock lock = new ReentrantLock();
	Map<Box, ExecutionSupport> knownInterpreters = new MapMaker().weakKeys()
								     .makeMap();
	private Out output;

	public FieldJython(Box root) {
		super(null);

		Log.log("startup.jython", ()->"Jython plugin is starting up ");


		Shims.init();

		Animatable.registerHandler((was, o) -> {
			if (o instanceof PyFunction) {
				log("jython.debug", ()->"jython found");
				return (Animatable.AnimationElement) isEnding -> ((PyFunction) o).__call__();
			}
			return was;
		});



		Log.log("startup.jython", ()->"Jython plugin has finished starting up ");


		new Thread(() -> {
			new PythonInterpreter();
		}).start();

	}

	private Stream<Box> selection() {
		return breadthFirst(both()).filter(x -> x.properties.isTrue(Mouse.isSelected, false));
	}

	@Override
	public ExecutionSupport support(Box box, Dict.Prop<String> prop) {
		FunctionOfBox<Boolean> ef = this.properties.get(executionFilter);
		if (box==this || ef == null || ef.apply(box)) return knownInterpreters.computeIfAbsent(box, k -> wrap(box, prop));
		else return null;
	}

	private ExecutionSupport wrap(Box box, Dict.Prop<String> prop) {

		return new ExecutionSupport() {

			public Dict.Prop<String> originProperty;
			public int lineOffset;
			public String filename;

			public Triple<Box, Integer, Boolean> currentLineNumber;

			PythonInterpreter py = new PythonInterpreter();

			long uniq;

			@Override
			public Object executeTextFragment(String textFragment, String suffix, Consumer<String> success, Consumer<Pair<Integer, String>> lineErrors) {

				if (suffix.equals("print")) textFragment = "print(" + textFragment + ")";

				try {
					Execution.context.get()
							 .push(box);

					currentLineNumber = null;


					final String finalTextFragment = textFragment;
					log("jython.debug", ()->" execute text fragment on :" + finalTextFragment);
					int[] written = {0};
					Writer w = new Writer() {
						@Override
						public void write(char[] cbuf, int off, int len) throws IOException {

							if (len > 0) {
								String s = new String(cbuf, off, len);
//							if (s.endsWith("\n"))
//								s = s.substring(0, s.length() - 1) + "<br>";
								if (s.trim().length() == 0) return;
								written[0]++;

								if (currentLineNumber == null || currentLineNumber.first == null || currentLineNumber.second == -1) {
									final String finalS = s;
									Set<Consumer<Quad<Box, Integer, String, Boolean>>> o = box.find(Execution.directedOutput, box.upwards())
																  .collect(Collectors.toSet());
									o.forEach(x -> x.accept(new Quad<>(box, -1, finalS, true)));

								}
								else {

									final String finalS = s;
									Set<Consumer<Quad<Box, Integer, String, Boolean>>> o = box.find(Execution.directedOutput, box.upwards())
																  .collect(Collectors.toSet());

									if (o.size()>0) {
										o.forEach(x -> x.accept(new Quad<>(currentLineNumber.first, currentLineNumber.second, finalS, currentLineNumber.third)));
									} else {
//									success.accept(finalS);

										o.forEach(x -> x.accept(new Quad<>(box, -1, finalS, true)));

									}
								}
							}

						}

						@Override
						public void flush() throws IOException {
						}

						@Override
						public void close() throws IOException {
						}
					};

					output.setWriter(w, this::setCurrentLineNumberForPrinting);

					StringBuffer prefix = new StringBuffer(Math.max(0, lineOffset));
					for (int i = 0; i < lineOffset; i++)
						prefix.append('\n');

					Object result = eval(box, w, prefix+textFragment, lineErrors);
					if (written[0] == 0) w.write("" + (result.equals(Py.None) ? " &#10003; " : ("" + result)));

					log("jython.debug", ()->" result string is " + result);
					return result;
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					Execution.context.get()
							 .pop();
				}
				return null;
			}


			@Override
			public Object getBinding(String name) {
				return py.get(name);
			}

			@Override
			public void executeAll(String allText, Consumer<Pair<Integer, String>> lineErrors, Consumer<String> success) {
				executeTextFragment(allText, "", success, lineErrors);
			}

			@Override
			public String begin(Consumer<Pair<Integer, String>> lineErrors, Consumer<String> success, Map<String, Object> initiator) {

				String allText = box.first(prop)
						    .orElse("");

				deleteVar("_r");

				initiator.entrySet()
					 .forEach(x -> {
						 if (x.getValue() == null) deleteVar(x.getKey());
						 else setVar(x.getKey(), x.getValue());
					 });

				executeAll(allText, lineErrors, success);


				Object ret = getVar("_r");

				log("jython.debug", ()->"_r at eXit :" + ret);

				log("jython.debug", ()->"invoking");


				Animatable.AnimationElement ae = Animatable.interpret(ret, null);


				if (ae != null) {
					end(lineErrors, success);
					String name = "main._animatorJython_" + (uniq);
					box.properties.putToMap(Boxes.insideRunLoop, name, new Animatable.Shim(ae));
					box.first(IsExecuting.isExecuting)
					   .ifPresent(x -> x.accept(box, name));

					uniq++;
					return name;
				}


				return null;
			}

			@Override
			public void end(Consumer<Pair<Integer, String>> lineErrors, Consumer<String> success) {
				Map<String, Supplier<Boolean>> m = box.properties.get(Boxes.insideRunLoop);
				if (m == null) return;
				for (String s : new ArrayList<>(m.keySet())) {
					if (s.contains("_animatorJython_")) {
						Supplier<Boolean> b = m.get(s);
						if (b instanceof Consumer) ((Consumer<Boolean>) b).accept(false);
						else {
							m.remove(s);
						}
					}
				}
				Drawing.dirty(box);
			}

			@Override
			public void setConsoleOutput(Consumer<String> stdout, Consumer<String> stderr) {
			}

			private void setCurrentLineNumberForPrinting(Triple<Box, Integer, Boolean> boxLine) {
				currentLineNumber = boxLine;
			}

			@Override
			public void completion(String allText, int line, int ch, Consumer<List<Completion>> results, boolean explicit) {

				if (!explicit) return;

				System.out.println(" entering completion ");

				String[] lines = allText.split("\n");

				String before = "";
				String after = "";

				for (int i = 0; i < line; i++)
					before += lines[i] + "\n";

				for (int i = line + 1; i < lines.length; i++)
					after += lines[i] + "\n";

				String sub = "";
				int subStart = ch;
				int subEnd = ch;

				for (int i = ch - 1; i >= 0; i--) {
					if (isValidPythonThing(lines[line].charAt(i))) {
						subStart = i;
						sub = lines[line].charAt(i) + sub;
					} else break;
				}

				for (int i = ch; i < lines[line].length(); i++) {
					if (isValidPythonThing(lines[line].charAt(i))) {
						subEnd = i + 1;
					} else break;
				}

				final int finalSubEnd = subEnd;
				final int finalSubStart = subStart;
				log("python.debug", ()->"parsed line <" + lines[line] + "> __prefix__ is " + finalSubStart + " -> " + finalSubEnd);

				String mid = lines[line].substring(0, subStart) + " __prefix__ " + lines[line].substring(subEnd, lines[line].length());

				final String finalSub = sub;
				log("python.debug", ()->"parsed line thus " + mid + " / " + finalSub);

				String prefix = lines[line].substring(0, subStart);
				if (prefix.endsWith(".")) prefix = prefix.substring(0, prefix.length() - 1);

				Object prefixIs = eval(box, null, prefix, null);

				if (prefixIs instanceof PyObject) prefixIs = Py.tojava((PyObject) prefixIs, Object.class);

				System.out.println(" evaluated :" + prefix + " to " + prefixIs + " getting completions on it");

				List<Completion> javaC = TernSupport.javaSupport.getCompletionsFor(prefixIs, sub);

				for (Completion x : javaC) {
					if (x.start == -1) x.start = before.length() + subStart;
					if (x.end == -1) x.end = before.length() + subEnd;
				}

				results.accept(javaC);

			}

			private boolean isValidPythonThing(char at) {
				return Character.isJavaIdentifierPart(at); //FIXME
			}


			@Override
			public void imports(String allText, int line, int ch, Consumer<List<Completion>> results) {

				List<Completion> r = new ArrayList<>();

				String[] lines = allText.split("\n");

				String before = "";
				String after = "";

				for (int i = 0; i < line; i++)
					before += lines[i] + "\n";

				for (int i = line + 1; i < lines.length; i++)
					after += lines[i] + "\n";

				String sub = "";
				int subStart = ch;
				int subEnd = ch;

				for (int i = ch - 1; i >= 0; i--) {
					if (isValidPythonThing(lines[line].charAt(i))) {
						subStart = i;
						sub = lines[line].charAt(i) + sub;
					} else break;
				}

				for (int i = ch; i < lines[line].length(); i++) {
					if (isValidPythonThing(lines[line].charAt(i))) {
						subEnd = i + 1;
					} else break;
				}
				List<Pair<String, String>> possibleJavaClassesFor = JavaSupport.javaSupport.getPossibleJavaClassesFor(sub);

				Log.log("completion.debug", ()->" possible javaclasses :" + possibleJavaClassesFor);

				subStart += before.length();
				subEnd += before.length();

				for (Pair<String, String> p : possibleJavaClassesFor) {
					int tail = p.first.lastIndexOf(".");

					Completion ex = new Completion(subStart, subEnd, p.first.substring(tail + 1), p.second);
					ex.header = "from " + p.first.substring(0, tail) + " import " + p.first.substring(tail + 1); // FIXME
					r.add(ex);
				}

				results.accept(r);
			}

			@Override
			public String getCodeMirrorLanguageName() {
				return "python";
			}

			@Override
			public String getDefaultFileExtension() {
				return ".py";
			}

			private Object getVar(String r) {
				return py.get(r, Object.class);
			}

			private void deleteVar(String r) {
				py.set(r, null);
			}

			private void setVar(String r, Object v) {
				py.set(r, v);
			}

			public void setFilenameForStacktraces(String filename) {
				this.filename = filename;
			}


			protected Object eval(Box box, Writer output, String textFragment, Consumer<Pair<Integer, String>> lineErrors) {

				py.set("_", box);

				if (output != null) py.setOut(output);
				try {
					PyObject o = py.eval(py.compile(textFragment, filename));

					System.err.println(" --> " + o);
					return o;

				} catch (Exception e) {
					e.printStackTrace();
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					String split = sw.toString();
					String[] pieces = split.split("\n");
					StringBuffer o = new StringBuffer();
					int line = -1;

					Pattern ln = Pattern.compile(" line ([0-9]+),");

					for (int i = 0; i < Math.min(8, pieces.length); i++) {
						if (pieces[i].trim()
							     .isEmpty()) break;
						o.append(pieces[i] + "\n");

						if (line != -1) continue;

						Matcher m = ln.matcher(pieces[i]);
						if (m.find()) {
							line = Integer.parseInt(m.group(1));
						}

					}
					if (lineErrors != null) lineErrors.accept(new Pair<>(line, TextUtils.html(o.toString())));
				}

				return null;
			}

			@Override
			public void setLineOffsetForFragment(int line, Dict.Prop<String> origin) {
				lineOffset = line;
				originProperty = origin;
			}
		};


	}


	@Override
	public void loaded() {
		output = this.find(Out.__out, both())
			    .findFirst()
			    .orElseThrow(() -> new IllegalStateException("Can't find html output support"));

	}
}

