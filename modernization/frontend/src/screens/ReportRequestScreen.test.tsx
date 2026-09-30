import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportRequestScreen } from './ReportRequestScreen';
import { stubFetch } from '../test/fixtures';

describe('Report request screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('submits a monthly report without asking for dates', async () => {
    const fetchMock = stubFetch({
      'POST /api/transaction-reports': {
        body: {
          requestId: 7,
          reportName: 'Monthly',
          startDate: '2022-06-01',
          endDate: '2022-06-30',
          submitted: true,
          message: 'Report request submitted',
        },
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<ReportRequestScreen onExit={() => {}} />);

    await userEvent.selectOptions(screen.getByLabelText('Report type'), 'MONTHLY');
    await userEvent.type(screen.getByLabelText('Confirm to print report (Y/N)'), 'Y');
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByText('2022-06-30')).toBeInTheDocument();
    expect(screen.queryByLabelText('Start Month')).not.toBeInTheDocument();
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body)).reportType).toBe('MONTHLY');
  });

  it('asks for the six date parts of a custom range', async () => {
    vi.stubGlobal('fetch', stubFetch({}));
    render(<ReportRequestScreen onExit={() => {}} />);

    await userEvent.selectOptions(screen.getByLabelText('Report type'), 'CUSTOM');

    expect(screen.getByLabelText('Start Month')).toBeInTheDocument();
    expect(screen.getByLabelText('End Year')).toBeInTheDocument();
  });

  it('reports the date edit the service refused', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'POST /api/transaction-reports': {
          status: 400,
          body: { status: 'START_DATE_INVALID', message: 'Start Date - Not a valid date...' },
        },
      }),
    );
    render(<ReportRequestScreen onExit={() => {}} />);

    await userEvent.selectOptions(screen.getByLabelText('Report type'), 'CUSTOM');
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Start Date - Not a valid date...',
    );
  });
});
