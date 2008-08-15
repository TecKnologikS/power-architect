package ca.sqlpower.architect;

/**
 * The Monitorable interface is a generic way for objects which perform certain
 * tasks to make their progress monitorable.  It is usually appropriate for the
 * class that performs the work to implement this interface directly, but there
 * are some cases where many classes share the work of one overall job, and in
 * that case it might be best for them to use a shared instance of
 * {@link MonitorableImpl}.
 *
 * <p>
 * If the interested party is a GUI component, this information can be interpreted
 * by a {@link ProgressWatcher} which will in turn drive a progress bar in the GUI.
 * Other types of user interfaces can provide similar generic classes that use a
 * Monitorable to track progress.
 */
public interface Monitorable {

	/**
	 * Tells how much work has been done.
	 *
	 * @return The amount of work that has been done so far (between 0 and the job size).
	 * @throws ArchitectException
	 */
	public int getProgress() throws ArchitectException;

	/**
	 * Tells the size of the job being preformed.  If the size is not yet known (because
	 * work needs to be done to calculate the job size), returns null.
	 *
	 * @return An Integer saying how much work must be done; null if this amount is not
	 * yet known.
	 * @throws ArchitectException
	 */
	public Integer getJobSize() throws ArchitectException;

	public boolean hasStarted() throws ArchitectException;

	/**
	 * Tells interested parties that the task being performed by this object is finished.
	 * Normally, getJobSize() and getProgress will return equal integers at this point,
	 * but this is not required.  For example, when the user cancels the operation, it will
	 * be finished even though we have not progressed to the end of the job.
	 *
	 * @return True if and only if the process is finished.
	 * @throws ArchitectException
	 */
	public boolean isFinished() throws ArchitectException;

	/**
	 * call this to get a message to stick in the dynamic portion of your ProgressMonitor
	 *
	 * @return
	 */
	public String getMessage();

	/**
	 * Lets the ProgressWatcher send a signal to a Monitorable
	 * telling it to cancel itself.
	 *
	 * @param cancelled
	 */
	public void setCancelled(boolean cancelled);

}