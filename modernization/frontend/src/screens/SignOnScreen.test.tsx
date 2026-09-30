import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SignOnScreen } from './SignOnScreen';

describe('Sign on screen', () => {
  it('sends the operator to the realm rather than asking for the password itself', async () => {
    const signOn = vi.fn();

    render(<SignOnScreen onSignOn={signOn} />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign on' }));

    expect(signOn).toHaveBeenCalledTimes(1);
    expect(screen.queryByLabelText('Password')).toBeNull();
  });

  it('shows the message of a refused sign-on on the message line of the map', () => {
    render(
      <SignOnScreen
        onSignOn={() => {}}
        message="Unable to verify the User ID and Password. Try again ..."
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Unable to verify the User ID and Password. Try again ...',
    );
  });

  it('does not offer the button twice while the realm has the operator', () => {
    render(<SignOnScreen onSignOn={() => {}} busy />);

    expect(screen.getByRole('button', { name: 'Sign on' })).toBeDisabled();
  });
});
