import { useEffect, useState } from 'react';
import { ApiError, fetchAdminMenu, selectAdminMenuOption } from '../api/client';
import type { MenuOptionResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COADM01C / map COADM01 - Admin Menu.
 *
 * <p>The options are the table of app/cpy/COADM02Y.cpy and the option is keyed as text, because
 * PROCESS-ENTER-KEY refuses a non-numeric, zero or out of range value with a single message. The
 * two transaction type options of the table are shown as the source shows them but are not part of
 * this conversion, so selecting one reports that instead of transferring.
 */
export function AdminMenuScreen({
  onSelect,
  onSignOff,
}: {
  onSelect: (program: string) => void;
  onSignOff: () => void;
}) {
  const [options, setOptions] = useState<MenuOptionResponse[]>([]);
  const [option, setOption] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  useEffect(() => {
    fetchAdminMenu()
      .then(setOptions)
      .catch((failure: unknown) => setError(describe(failure)));
  }, []);

  async function select(keyed: string) {
    setError(null);
    setInfo(null);
    try {
      const selected = await selectAdminMenuOption(keyed);
      if (selected.program.startsWith('COUSR')) {
        onSelect(selected.program);
        return;
      }
      setInfo(`${selected.name} (${selected.program}) is not part of this conversion`);
    } catch (failure: unknown) {
      setError(describe(failure));
    }
  }

  return (
    <ScreenFrame transaction="CAAD" program="COADM01C" title="Admin Menu">
      <ul>
        {options.map((entry) => (
          <li key={entry.optionNumber} style={{ marginBottom: '0.4rem' }}>
            <button
              type="button"
              className="primary"
              onClick={() => void select(String(entry.optionNumber))}
            >
              {String(entry.optionNumber).padStart(2, '0')}. {entry.name}
            </button>
          </li>
        ))}
      </ul>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void select(option);
        }}
      >
        <div className="field">
          <label htmlFor="option">Option</label>
          <div className="field__group">
            <input
              id="option"
              aria-label="Option"
              value={option}
              maxLength={2}
              size={2}
              onChange={(event) => setOption(event.target.value)}
            />
            <button type="submit">Enter</button>
            <button type="button" onClick={onSignOff}>
              F3 - Sign off
            </button>
          </div>
        </div>
      </form>

      <Messages info={info} error={error} />
    </ScreenFrame>
  );
}

function describe(failure: unknown): string {
  return failure instanceof ApiError ? failure.message : 'Unable to read the menu';
}
