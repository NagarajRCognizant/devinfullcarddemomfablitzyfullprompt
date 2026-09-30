import { useEffect, useState } from 'react';
import { ApiError, fetchMenu } from '../api/client';
import type { MenuResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COMEN01C / map COMEN01 - Main Menu, holding the two account management options of
 * app/cpy/COMEN02Y.cpy that are in scope.
 *
 * <p>The access rule of PROCESS-ENTER-KEY runs on the server for the SEC-USR-TYPE the token
 * carries, so an option this operator type may not select arrives already marked inaccessible.
 */
export function MenuScreen({
  onSelect,
  onSignOff,
}: {
  onSelect: (program: string) => void;
  onSignOff: () => void;
}) {
  const [menu, setMenu] = useState<MenuResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchMenu()
      .then(setMenu)
      .catch((failure: unknown) =>
        setError(failure instanceof ApiError ? failure.message : 'Unable to read the menu'),
      );
  }, []);

  return (
    <ScreenFrame transaction="CM00" program="COMEN01C" title="Main Menu">
      <p>
        {menu?.userId} - {menu?.userTypeName}
      </p>
      <ul>
        {(menu?.options ?? []).map((option) => (
          <li key={option.number} style={{ marginBottom: '0.4rem' }}>
            <button
              type="button"
              className="primary"
              disabled={!option.accessible}
              onClick={() => onSelect(option.program)}
            >
              {String(option.number).padStart(2, '0')}. {option.name}
            </button>
          </li>
        ))}
      </ul>
      {/*
        CPVS is not an entry of the COMEN02Y option table: the CSD installs it as its own
        transaction, which an operator reached by typing the transaction id. The table is therefore
        left as the source has it and the authorization screen is reached by its own entry point.
      */}
      <button type="button" onClick={() => onSelect('COPAUS0C')}>
        CPVS - Pending Authorizations
      </button>
      <button type="button" onClick={onSignOff}>
        F3 - Sign off
      </button>
      <Messages error={error} />
    </ScreenFrame>
  );
}
