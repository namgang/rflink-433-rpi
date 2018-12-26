import groovy.transform.MapConstructor

/*
 * Analyze a pulse file, print its messages and timing statistics.
 * Also write a ".clean" file for manual debugging.
 *
 * Command line argument: one or more filenames ending with ".pulse"
 *
 * In addition to the script the file contains an enum and an
 * auxiliary class.
 */

CLEAN = '.clean'
PULSE = '.pulse'
NEXA = ~/S([01]{64})P/

/**
 * Analyze a pulse file.
 * @param filename must be the basic file name without
 * The ".pulse" filename extension.
 */
def doit(String filename) {
  println "======= Analyzing ${filename} ==="
  def buffer = new ArrayList<Token>(200)
  def input = new File(filename + PULSE)
  input.eachLine {line ->
    def pieces = line.split(/;/)
    def signal = [time: Integer.parseInt(pieces[0].trim()),
		  high: Integer.parseInt(pieces[1].trim()),
		  low: Integer.parseInt(pieces[2].trim())]
    process(signal, buffer)
  }

  def cleanList = postProcessAndWrite(filename, buffer)
  def stats = computeStatistics(cleanList)
  printStats(stats)
  List<String> messages = printMessages(cleanList)
  printNexa(messages)
}

/**
 * Create a Token from a single signal.
 * Add it to a buffer.
 * SIDE EFFECT: A Token is added to the buffer.
 */
def process(Map sig, List<Token> buffer) {
  def categ = null
  if (isZero(sig)) categ = Thisis.ZERO
  else if (isOne(sig)) categ = Thisis.ONE
  else if (isPause(sig)) categ = Thisis.PAUSE
  else if (isSync(sig)) categ = Thisis.SYNC
  else categ = Thisis.FAIL
  buffer << new Token(timestamp: sig.time, category: categ,
		    highMicros: sig.high, lowMicros: sig.low)
}

/**
 * Filter out spurious input, try to get hold of the message.
 * Write a ".clean" file with our interpretation of the input.
 */
List<Token> postProcessAndWrite(String filename, List<Token> buffer) {
  def clean = new ArrayList<Token>(200)
  def it = buffer.listIterator()
  // Skip over all FAILures
  while (it.hasNext() && it.next().category == Thisis.FAIL) {}
  it.previous()
  def state = Thisis.FAIL

  def output = new File(filename + CLEAN)
  def fw = new FileWriter(output)
  def pw = new PrintWriter(fw)
  while (it.hasNext()) {
    def current = it.next()
    while (it.hasNext()) {
      def succ = it.next()
      if (succ.failure) {
	current.merge(succ)
      } else {
	it.previous()
	break
      }
    }

    if (current.sync && state == Thisis.PAUSE) {
      state = Thisis.SYNC
    } else if (!current.digit) {
      state = Thisis.FAIL
    }

    if (state == Thisis.SYNC || current.pause) {
      pw.println(current)
      clean << current
    }

    if (current.pause) state = Thisis.PAUSE
  }

  pw.flush()
  pw.close()
  return clean
}

/**
 * Collect minimum and maximum timings.
 * The result is used to modify the timings of this script.
 */
Map computeStatistics(List<Token> list) {
  def data = [:]
  list.each {token ->
    def entry = data[token.category]
    if (!entry) {
      entry = [count: 0, min: Token.ofZeros(token), max: Token.ofZeros(token)]
      data[token.category] = entry
    }

    ++entry.count
    entry.min.minimum(token)
    entry.max.maximum(token)
  }

  return data
}

/**
 * Print statistics.
 * @param data must be a map created by computeStatistics.
 */
def printStats(Map data) {
  Thisis.values().each {category ->
    if (category == Thisis.FAIL) return
    def entry = data[category]
    println "${category} (${entry.count}) ------------"
    def times = computeStats(entry, category)
    println "High: ${times.minHigh} .. ${times.maxHigh} (${times.avgHigh})"
    println " Low: ${times.minLow} .. ${times.maxLow} (${times.avgLow})"
  }
}

/**
 * Do the math of finding the average in addition to minimum and
 * maximum timings.
 */
private Map computeStats(Map entry, Thisis category) {
  def minHigh = entry.min.highMicros
  def maxHigh = entry.max.highMicros
  def avgHigh = avg(minHigh, maxHigh)
  def minLow = entry.min.lowMicros
  def maxLow = entry.max.lowMicros
  def avgLow = avg(minLow, maxLow)
  [minHigh: minHigh, maxHigh: maxHigh, avgHigh: avgHigh,
   minLow: minLow, maxLow: maxLow, avgLow: avgLow]
}

private int avg(int min, int max) {
  def sum = min + max
  return (sum + sum % 2) / 2
}

/**
 * Print what we consider the messages after distilling.
 */
private List<String> printMessages(List<Token> list) {
  def sb = new StringBuilder()
  boolean active = false
  int messageIndex = 0
  def output = []
  list.each {token ->
    if (token.sync) {
      active = true
      if (sb.length() > 0) {
	message = messageOutput(++messageIndex, sb)
	output << message
	sb.length = 0
      }
    }

    if (active) {
      if (token.sync) {
	sb.append('S')
      } else if (token.one) {
	sb.append('1')
      } else if (token.zero) {
	sb.append('0')
      } else if (token.pause) {
	sb.append('P')
      } else {
	sb.append('x')
      }
    }
  }

  if (sb.length() > 0) {
    def message = messageOutput(++messageIndex, sb)
    output << message
  }

  return output
}

/**
 * Print what we consider to be NEXA message candidates, if any.
 * There may be more than one.
 */
private printNexa(List<String> messages) {
  def counter = [:]
  messages.each {msg ->
    Integer count = counter[msg]
    if (msg.size() >= 64) {
      if (count == null) {
	counter[msg] = 1
      } else {
	counter[msg] = count + 1
      }
    }
  }

  // Invert the messages map
  def sorted = new TreeMap()
  counter.each {entry ->
    def msg = sorted[entry.value]
    if (!msg) {
      sorted[entry.value] = [entry.key]
    } else {
      sorted[entry.value] << entry.key
    }
  }

  def lastEntry = sorted.lastEntry()?.value ?: []
  println '--- NEXA Candidate -----'
  lastEntry.each {msg ->
    println "    ${msg} (${msg.size()})"
    def m = NEXA.matcher(msg)
    if (m.matches()) {
      def hex = Long.parseUnsignedLong(m.group(1), 2)
      println "   <S${hex}P>"
    }
  }
}

private String messageOutput(int idx, StringBuilder sb) {
  String msg = sb.toString().replaceAll(~/SS/, 'S').replaceAll(~/PP/, 'P')
  println String.format('%2d: %s', idx, msg)
  return msg
}

boolean isZero(Map sig) {
  sig.high in 210..310 && sig.low in 210..340
}

boolean isOne(Map sig) {
  sig.high in 210..310 && sig.low in 1220..1400
}

boolean isPause(Map sig) {
  sig.high in 210..310 && sig.low > 9000
}

boolean isSync(Map sig) {
  sig.high in 210..310 && sig.low in 2500..2850
}

enum Thisis {
  SYNC,
  ONE,
  ZERO,
  PAUSE,
  FAIL
}

/**
 * A Token consists of a High pulse followed by a Low.
 * All time units are microseconds.
 */
@MapConstructor
class Token {
  // Point in time when the token appears.
  int timestamp

  // Preliminary token category
  Thisis category

  // Duration of the High pulse.
  int highMicros

  // Duration of the Low portion up to the next Token.
  int lowMicros

  static Token ofZeros(Token token) {
    new Token(category: token.category, timestamp: 0,
	      highMicros: 0, lowMicros: 0)
  }

  /**
   * Merge another Token.
   * You must make sure it occurs immediately after this token.
   * The effect is to ignore the other token.
   */
  Token merge(Token other) {
    new Token(timestamp: timestamp, highMicros: highMicros,
	      lowMicros: lowMicros + other.totalTime)
  }

  def maximum(Token other) {
    if (other.highMicros > highMicros) highMicros = other.highMicros
    if (other.lowMicros > highMicros) lowMicros = other.lowMicros
  }

  def minimum(Token other) {
    if (highMicros == 0 || other.highMicros < highMicros) {
      highMicros = other.highMicros
    }
    if (lowMicros == 0 || other.lowMicros < lowMicros) {
      lowMicros = other.lowMicros
    }
  }

  boolean isDigit() {
    category == Thisis.ONE || category == Thisis.ZERO
  }

  boolean isFailure() {
    category == Thisis.FAIL
  }

  boolean isOne() {
    category == Thisis.ONE
  }

  boolean isPause() {
    category == Thisis.PAUSE
  }

  boolean isSync() {
    category == Thisis.SYNC
  }

  int getTotalTime() {
    highMicros + lowMicros
  }

  boolean isZero() {
    category == Thisis.ZERO
  }

  String toString() {
    String.format('%8d; %5s; %5d; %5d', timestamp, category, highMicros,
		 lowMicros)
  }

}

/*
 * Kick off the script from one or more file names.
 */
if (args.size() < 1) {
  println 'File name required'
} else {
  int conflictCount = 0
  def filenames = args.collect {filename ->
    def idx = filename.lastIndexOf(PULSE)
    boolean fileNameOk = idx >= 0
    if (!fileNameOk) ++conflictCount
    return fileNameOk? filename.substring(0, idx) : null
  }

  if (conflictCount > 0) {
    println "All file names must end with .pulse (${conflictCount} conflicts)"
  } else {
    filenames.each {doit(it)}
  }
}
