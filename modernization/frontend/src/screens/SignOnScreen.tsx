import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COSGN00C / map COSGN00 - Sign on.
 *
 * <p>The two fields of the map are no longer here. The realm asks for the user id, the password and
 * the one-time code on its own login pages, so the credentials never pass through this page and there
 * is nothing for it to send anywhere; what is left of the map is the screen the operator started from
 * and the message line the source wrote its sign-on failures on.
 */
export function SignOnScreen({
  onSignOn,
  message,
  busy,
}: {
  onSignOn: () => void;
  message?: string | null;
  busy?: boolean;
}) {
  return (
    <ScreenFrame transaction="CC00" program="COSGN00C" title="Sign on">
      <p>
        Sign on with your User ID, password and the one-time code from your authenticator to reach the
        CardDemo menus.
      </p>
      <button type="button" className="primary" disabled={busy} onClick={onSignOn}>
        Sign on
      </button>
      <Messages error={message ?? null} />
    </ScreenFrame>
  );
}
