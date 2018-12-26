public class Edge implements Comparable<Edge> {
    /**
     * Time in micros from the start time.
     */
    public final Long timeMicros;

    /**
     * The measurement, 1 for a rising edge, 0 otherwise.
     */
    public final int signal;

    public Edge(Long timeMicros, int signal) {
	this.timeMicros = timeMicros;
	this.signal = signal;
    }

    public Long diff(Edge other) {
	return timeMicros - other.timeMicros;
    }

    public String toString() {
	return "Edge(" + timeMicros + ", " + signal + ")";
    }

    public int compareTo(Edge other) {
	return timeMicros.compareTo(other.timeMicros);
    }

}
