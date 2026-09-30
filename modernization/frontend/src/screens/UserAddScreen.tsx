import { useState } from 'react';
import { ApiError, addUser } from '../api/client';
import type { FieldFlag, UserAddForm } from '../api/types';
import { Field, FieldRow } from '../components/Field';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

const EMPTY: UserAddForm = {
  firstName: '',
  lastName: '',
  userId: '',
  password: '',
  userType: '',
};

/**
 * COUSR01C / map COUSR01 - Add User.
 *
 * <p>All five fields are required and are checked in map order, one message at a time, so the form
 * is submitted as keyed and the service decides which field to report. A successful write clears
 * the map, as the source does before redisplaying it.
 */
export function UserAddScreen({ onExit }: { onExit: () => void }) {
  const [form, setForm] = useState<UserAddForm>(EMPTY);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [flags, setFlags] = useState<Record<string, FieldFlag | undefined>>({});

  async function submit() {
    setInfo(null);
    setError(null);
    setFlags({});
    try {
      const response = await addUser(form);
      setInfo(response.message);
      setForm(EMPTY);
    } catch (failure: unknown) {
      if (failure instanceof ApiError) {
        setError(failure.message);
        setFlags(failure.body.fieldFlags ?? {});
      } else {
        setError('Unable to Add User...');
      }
    }
  }

  const field = (name: keyof UserAddForm, label: string, maxLength: number) => (
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
    <ScreenFrame transaction="CU01" program="COUSR01C" title="Add User">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {field('firstName', 'First Name', 20)}
        {field('lastName', 'Last Name', 20)}
        {field('userId', 'User ID', 8)}
        {field('password', 'Password', 8)}
        {field('userType', 'User Type', 1)}
        <div className="field__group">
          <button type="submit" className="primary">
            Enter
          </button>
          <button type="button" onClick={() => setForm(EMPTY)}>
            F4 - Clear
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
