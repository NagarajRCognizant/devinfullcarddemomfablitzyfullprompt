import { useCallback, useEffect, useState } from 'react';
import { ApiError, listUsers } from '../api/client';
import type { BrowseDirection, UserListResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COUSR00C / map COUSR00 - List Users.
 *
 * <p>The map holds ten rows and the browse is keyed, not offset based: PF8 reads forward from the
 * last id shown and PF7 backwards from the first, which is why the page is requested with a
 * direction and a key rather than a page index. Each row carries the U and D selections of the
 * source, which transfer to the update and delete screens with that row's user id.
 */
export function UserListScreen({
  onSelect,
  onExit,
}: {
  onSelect: (program: 'COUSR02C' | 'COUSR03C', userId: string) => void;
  onExit: () => void;
}) {
  const [filter, setFilter] = useState('');
  const [page, setPage] = useState<UserListResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selections, setSelections] = useState<Record<string, string>>({});

  const load = useCallback(
    async (direction: BrowseDirection, key: string, pageNumber: number) => {
      setError(null);
      try {
        const loaded = await listUsers(direction, key, pageNumber);
        // PROCESS-PF7-KEY and PROCESS-PF8-KEY set SEND-ERASE-NO at the boundaries, so the page on
        // display stays on display and only the message line changes.
        const boundary = direction !== 'FIRST' && loaded.users.length === 0 && !!loaded.message;
        setPage((shown) =>
          boundary && shown !== null ? { ...shown, message: loaded.message } : loaded,
        );
      } catch (failure: unknown) {
        setError(failure instanceof ApiError ? failure.message : 'Unable to read the user file');
      }
    },
    [],
  );

  useEffect(() => {
    void load('FIRST', '', 0);
  }, [load]);

  /** PROCESS-PAGE-FORWARD: the browse resumes after the last id the map showed. */
  function forward() {
    void load('NEXT', page?.lastUserId ?? '', page?.pageNumber ?? 0);
  }

  /** PROCESS-PAGE-BACKWARD: READPREV from the first id the map showed. */
  function backward() {
    void load('PREVIOUS', page?.firstUserId ?? '', page?.pageNumber ?? 0);
  }

  function act(userId: string) {
    const keyed = (selections[userId] ?? '').trim().toUpperCase();
    if (keyed === 'U') {
      onSelect('COUSR02C', userId);
      return;
    }
    if (keyed === 'D') {
      onSelect('COUSR03C', userId);
      return;
    }
    setError('Invalid selection. Valid values are U and D');
  }

  return (
    <ScreenFrame transaction="CU00" program="COUSR00C" title="List Users">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void load('FIRST', filter, 0);
        }}
      >
        <div className="field">
          <label htmlFor="filter">User ID</label>
          <div className="field__group">
            <input
              id="filter"
              aria-label="User ID"
              value={filter}
              maxLength={8}
              size={8}
              onChange={(event) => setFilter(event.target.value)}
            />
            <button type="submit" className="primary">
              Search
            </button>
          </div>
        </div>
      </form>

      <table>
        <thead>
          <tr>
            <th scope="col">Sel</th>
            <th scope="col">User ID</th>
            <th scope="col">First Name</th>
            <th scope="col">Last Name</th>
            <th scope="col">Type</th>
            <th scope="col" />
          </tr>
        </thead>
        <tbody>
          {(page?.users ?? []).map((user) => (
            <tr key={user.userId}>
              <td>
                <input
                  aria-label={`Selection for ${user.userId}`}
                  value={selections[user.userId] ?? ''}
                  maxLength={1}
                  size={1}
                  onChange={(event) =>
                    setSelections({ ...selections, [user.userId]: event.target.value })
                  }
                />
              </td>
              <td>{user.userId}</td>
              <td>{user.firstName}</td>
              <td>{user.lastName}</td>
              <td>{user.userType}</td>
              <td>
                <button type="button" onClick={() => act(user.userId)}>
                  Enter
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="field__group">
        <button type="button" onClick={backward}>
          F7 - Backward
        </button>
        <button type="button" onClick={forward}>
          F8 - Forward
        </button>
        <button type="button" onClick={onExit}>
          F3 - Back
        </button>
        <span>Page {page?.pageNumber ?? 0}</span>
      </div>

      <Messages info={page?.message} error={error} />
    </ScreenFrame>
  );
}
