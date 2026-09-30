import { useState } from 'react';
import { ApiError, submitReportRequest } from '../api/client';
import type { ReportRequestForm, ReportRequestResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

const EMPTY: ReportRequestForm = {
  reportType: '',
  startMonth: '',
  startDay: '',
  startYear: '',
  endMonth: '',
  endDay: '',
  endYear: '',
  confirm: '',
};

/**
 * CORPT00C / map CORPT0A - Transaction Reports (transaction CR00).
 *
 * <p>The source wrote JCL to the TDQ JOBS so that the internal reader started the report job.
 * That submission is replaced by a report request the batch job picks up, which is the one
 * platform change: the three report types, the date edits and the print confirmation are the
 * source's, and the monthly and yearly ranges are still derived from the current date.
 */
export function ReportRequestScreen({ onExit }: { onExit: () => void }) {
  const [form, setForm] = useState<ReportRequestForm>(EMPTY);
  const [submitted, setSubmitted] = useState<ReportRequestResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  function set(field: keyof ReportRequestForm, value: string) {
    setForm({ ...form, [field]: value });
  }

  async function submit() {
    setError(null);
    setInfo(null);
    try {
      const response = await submitReportRequest(form);
      setSubmitted(response);
      setInfo(response.message);
      if (response.submitted) {
        setForm(EMPTY);
      }
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Unable to submit report request');
    }
  }

  const custom = form.reportType.toUpperCase() === 'CUSTOM';

  return (
    <ScreenFrame transaction="CR00" program="CORPT00C" title="Transaction Reports">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <div className="field">
          <label htmlFor="reportType">Report type</label>
          <div className="field__group">
            <select
              id="reportType"
              aria-label="Report type"
              value={form.reportType}
              onChange={(event) => set('reportType', event.target.value)}
            >
              <option value="">Select</option>
              <option value="MONTHLY">Monthly</option>
              <option value="YEARLY">Yearly</option>
              <option value="CUSTOM">Custom</option>
            </select>
          </div>
        </div>

        {custom
          ? (
              [
                ['Start Month', 'startMonth', 2],
                ['Start Day', 'startDay', 2],
                ['Start Year', 'startYear', 4],
                ['End Month', 'endMonth', 2],
                ['End Day', 'endDay', 2],
                ['End Year', 'endYear', 4],
              ] as [string, keyof ReportRequestForm, number][]
            ).map(([label, field, length]) => (
              <div className="field" key={field}>
                <label htmlFor={field}>{label}</label>
                <div className="field__group">
                  <input
                    id={field}
                    aria-label={label}
                    value={form[field]}
                    maxLength={length}
                    size={length}
                    onChange={(event) => set(field, event.target.value)}
                  />
                </div>
              </div>
            ))
          : null}

        <div className="field">
          <label htmlFor="reportConfirm">Confirm to print report (Y/N)</label>
          <div className="field__group">
            <input
              id="reportConfirm"
              aria-label="Confirm to print report (Y/N)"
              value={form.confirm}
              maxLength={1}
              size={1}
              onChange={(event) => set('confirm', event.target.value)}
            />
          </div>
        </div>

        <div className="field__group">
          <button type="submit" className="primary">
            Submit
          </button>
          <button type="button" onClick={() => setForm(EMPTY)}>
            Clear
          </button>
          <button type="button" onClick={onExit}>
            Back
          </button>
        </div>
      </form>

      {submitted?.submitted ? (
        <dl className="summary">
          <dt>Report</dt>
          <dd>{submitted.reportName}</dd>
          <dt>From</dt>
          <dd>{submitted.startDate}</dd>
          <dt>To</dt>
          <dd>{submitted.endDate}</dd>
          <dt>Request</dt>
          <dd>{submitted.requestId}</dd>
        </dl>
      ) : null}

      <Messages info={info} error={error} />
    </ScreenFrame>
  );
}
