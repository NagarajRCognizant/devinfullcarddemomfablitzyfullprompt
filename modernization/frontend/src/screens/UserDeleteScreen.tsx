import { useCallback, useEffect, useState } from 'react';
import { ApiError, deleteUser, fetchUser } from '../api/client';
import type { UserDetail } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COUSR03C / map COUSR03 - Delete User.
 *
 * <p>Every field of this map is protected: the record is read and displayed first, and F5 performs
 * the delete, so the operator confirms a record that is already on screen.
 */
export function UserDeleteScreen({
  userId: initialUserId,
  onExit,
}: {
  userId: string;
  onExit: () => void;
}) {
  const [userId, setUserId] = useState(initialUserId);
  const [user, setUser] = useState<UserDetail | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (key: string) => {
    setInfo(null);
    setError(null);
    if (!key.trim()) {
      return;
    }
    try {
      setUser(await fetchUser(key));
    } catch (failure: unknown) {
      setUser(null);
      setError(failure instanceof ApiError ? failure.message : 'Unable to Update User...');
    }
  }, []);

  useEffect(() => {
    void load(initialUserId);
  }, [initialUserId, load]);

  async function remove() {
    setInfo(null);
    setError(null);
    try {
      const response = await deleteUser(userId);
      setInfo(response.message);
      setUser(null);
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Unable to Update User...');
    }
  }

  return (
    <ScreenFrame transaction="CU03" program="COUSR03C" title="Delete User">
      <div className="field">
        <label htmlFor="userId">User ID</label>
        <div className="field__group">
          <input
            id="userId"
            aria-label="User ID"
            value={userId}
            maxLength={8}
            size={8}
            onChange={(event) => setUserId(event.target.value)}
          />
          <button type="button" onClick={() => void load(userId)}>
            Fetch
          </button>
        </div>
      </div>

      <dl>
        <dt>First Name</dt>
        <dd>{user?.firstName ?? ''}</dd>
        <dt>Last Name</dt>
        <dd>{user?.lastName ?? ''}</dd>
        <dt>User Type</dt>
        <dd>{user?.userType ?? ''}</dd>
      </dl>

      <div className="field__group">
        <button type="button" className="primary" disabled={!user} onClick={() => void remove()}>
          F5 - Delete
        </button>
        <button type="button" onClick={onExit}>
          F3 - Back
        </button>
      </div>

      <Messages info={info} error={error} />
    </ScreenFrame>
  );
}
