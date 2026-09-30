import { useCallback, useEffect, useState } from 'react';
import { ApiError, fetchUser, updateUser } from '../api/client';
import type { FieldFlag, UserUpdateForm } from '../api/types';
import { Field, FieldRow } from '../components/Field';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

const EMPTY: UserUpdateForm = { firstName: '', lastName: '', password: '', userType: '' };

/**
 * COUSR02C / map COUSR02 - Update User.
 *
 * <p>The record is read first, as READ-USER-SEC-FILE does, so the map opens on the stored values.
 * A submission in which nothing differs is refused by the service with "Please modify to update
 * ...", which is why the client does not compare the fields itself.
 */
export function UserUpdateScreen({
  userId: initialUserId,
  onExit,
}: {
  userId: string;
  onExit: () => void;
}) {
  const [userId, setUserId] = useState(initialUserId);
  const [form, setForm] = useState<UserUpdateForm>(EMPTY);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [flags, setFlags] = useState<Record<string, FieldFlag | undefined>>({});

  const load = useCallback(async (key: string) => {
    setInfo(null);
    setError(null);
    setFlags({});
    if (!key.trim()) {
      return;
    }
    try {
      const stored = await fetchUser(key);
      setForm({
        firstName: stored.firstName,
        lastName: stored.lastName,
        password: stored.password,
        userType: stored.userType,
      });
    } catch (failure: unknown) {
      setForm(EMPTY);
      setError(failure instanceof ApiError ? failure.message : 'Unable to Update User...');
    }
  }, []);

  useEffect(() => {
    void load(initialUserId);
  }, [initialUserId, load]);

  async function submit() {
    setInfo(null);
    setError(null);
    setFlags({});
    try {
      const response = await updateUser(userId, form);
      setInfo(response.message);
    } catch (failure: unknown) {
      if (failure instanceof ApiError) {
        setError(failure.message);
        setFlags(failure.body.fieldFlags ?? {});
      } else {
        setError('Unable to Update User...');
      }
    }
  }

  const field = (name: keyof UserUpdateForm, label: string, maxLength: number) => (
    <FieldRow label={label} htmlFor={name}>
      <Field
        label={label}
        name={name}
        value={form[name]}
        maxLength={maxLength}
        flag={flags[name]}
        onChange={(value) => setForm({ ...form, [name]: value })}
      />
    </FieldRow>
  );

  return (
    <ScreenFrame transaction="CU02" program="COUSR02C" title="Update User">
      <FieldRow label="User ID" htmlFor="userId">
        <Field
          label="User ID"
          name="userId"
          value={userId}
          maxLength={8}
          flag={flags.userId}
          onChange={setUserId}
        />
        <button type="button" onClick={() => void load(userId)}>
          Fetch
        </button>
      </FieldRow>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {field('firstName', 'First Name', 20)}
        {field('lastName', 'Last Name', 20)}
        {field('password', 'Password', 8)}
        {field('userType', 'User Type', 1)}
        <div className="field__group">
          <button type="submit" className="primary">
            Enter
          </button>
          <button type="button" onClick={onExit}>
            F3 - Back
          </button>
        </div>
      </form>
      <Messages info={info} error={error} />
    </ScreenFrame>
  );
}
