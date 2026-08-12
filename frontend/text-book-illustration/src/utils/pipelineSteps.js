export const STEPS = ['STYLE', 'CHARACTER', 'PORTRAIT', 'CHAPTER', 'ILLUSTRATION'];

/**
 * Calculates the number of fully completed steps (0-5).
 * The current step counts as completed only once its stepStatus is 'SUCCESS'.
 * PENDING / RUNNING / FAILED means preceding steps are complete, but current step is in-flight or failed.
 *
 * @param {string} step - Current pipeline step name (e.g. 'STYLE')
 * @param {string} stepStatus - Status of the step (e.g. 'SUCCESS', 'RUNNING', 'PENDING', 'FAILED')
 * @returns {number} Integer from 0 to 5
 */
export function completedStepCount(step, stepStatus) {
  if (!step) return 0;
  const idx = STEPS.indexOf(step);
  if (idx === -1) return 0;
  return idx + (stepStatus === 'SUCCESS' ? 1 : 0);
}
