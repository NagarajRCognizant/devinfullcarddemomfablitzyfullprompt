import { useEffect, useState } from 'react';
import { fetchCurrentUser, setAccessToken, setAccessTokenSource } from './api/client';
import {
  currentAccessToken,
  restoreSession,
  signIn,
  signOnMessage,
  signOut,
} from './auth/session';
import type { CurrentUserResponse } from './api/types';
import { AccountUpdateScreen } from './screens/AccountUpdateScreen';
import { AccountViewScreen } from './screens/AccountViewScreen';
import { AdminMenuScreen } from './screens/AdminMenuScreen';
import { AuthorizationDetailScreen } from './screens/AuthorizationDetailScreen';
import { AuthorizationSummaryScreen } from './screens/AuthorizationSummaryScreen';
import { BillPaymentScreen } from './screens/BillPaymentScreen';
import { CardListScreen } from './screens/CardListScreen';
import { CardUpdateScreen } from './screens/CardUpdateScreen';
import { CardViewScreen } from './screens/CardViewScreen';
import { MenuScreen } from './screens/MenuScreen';
import { ReportRequestScreen } from './screens/ReportRequestScreen';
import { SignOnScreen } from './screens/SignOnScreen';
import { TransactionAddScreen } from './screens/TransactionAddScreen';
import { TransactionListScreen } from './screens/TransactionListScreen';
import { TransactionViewScreen } from './screens/TransactionViewScreen';
import { UserAddScreen } from './screens/UserAddScreen';
import { UserDeleteScreen } from './screens/UserDeleteScreen';
import { UserListScreen } from './screens/UserListScreen';
import { UserUpdateScreen } from './screens/UserUpdateScreen';

type Screen =
  | 'COSGN00C'
  | 'COMEN01C'
  | 'COADM01C'
  | 'COACTVWC'
  | 'COACTUPC'
  | 'COUSR00C'
  | 'COUSR01C'
  | 'COUSR02C'
  | 'COUSR03C'
  | 'COPAUS0C'
  | 'COPAUS1C'
  | 'COCRDLIC'
  | 'COCRDSLC'
  | 'COCRDUPC'
  | 'COTRN00C'
  | 'COTRN01C'
  | 'COTRN02C'
  | 'CORPT00C'
  | 'COBIL00C';

/**
 * The XCTL chain of the CardDemo screens as in-page state.
 *
 * <p>Sign-on still comes first because every other transaction required a signed-on COMMAREA, but the
 * realm performs it: the page sends the operator there, receives a token back on the redirect, and
 * asks the identity service which USRSEC row it belongs to. That answer decides which menu follows -
 * COADM01C for SEC-USR-TYPE 'A', COMEN01C for everyone else - which is the routing of COSGN00C.
 * Signing off ends the session at the realm as well, so the next sign-on asks for both factors again.
 */
export function App() {
  const [screen, setScreen] = useState<Screen>('COSGN00C');
  const [session, setSession] = useState<CurrentUserResponse | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(true);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [selectedAuthorization, setSelectedAuthorization] =
    useState<{ accountId: number; authKey: string } | null>(null);
  const [selectedTranId, setSelectedTranId] = useState<string | null>(null);
  const [selectedCard, setSelectedCard] =
    useState<{ accountId: string; cardNumber: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const state = await restoreSession();
      if (cancelled) {
        return;
      }
      if (state.status !== 'signedIn') {
        setAccessToken(null);
        setMessage(state.status === 'signedOut' ? (state.message ?? null) : null);
        setBusy(false);
        return;
      }
      setAccessTokenSource(currentAccessToken);
      try {
        const signedOn = await fetchCurrentUser();
        if (cancelled) {
          return;
        }
        setSession(signedOn);
        setScreen(signedOn.nextScreen);
      } catch (failure: unknown) {
        // A token of the realm for somebody with no USRSEC row cannot reach a screen, which is the
        // NOTFND path of READ-USER-SEC-FILE.
        setAccessToken(null);
        setMessage(failure instanceof Error ? failure.message : signOnMessage(failure));
      }
      setBusy(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  function signOff() {
    setAccessToken(null);
    setSession(null);
    setScreen('COSGN00C');
    void signOut();
  }

  function menu() {
    setScreen(session?.nextScreen ?? 'COSGN00C');
  }

  if (!session) {
    return (
      <SignOnScreen
        busy={busy}
        message={message}
        onSignOn={() => {
          setBusy(true);
          void signIn().catch((failure: unknown) => {
            setMessage(signOnMessage(failure));
            setBusy(false);
          });
        }}
      />
    );
  }

  switch (screen) {
    case 'COACTVWC':
      return <AccountViewScreen onExit={menu} />;
    case 'COACTUPC':
      return <AccountUpdateScreen onExit={menu} />;
    case 'COUSR00C':
      return (
        <UserListScreen
          onExit={() => setScreen('COADM01C')}
          onSelect={(program, userId) => {
            setSelectedUserId(userId);
            setScreen(program);
          }}
        />
      );
    case 'COUSR01C':
      return <UserAddScreen onExit={() => setScreen('COADM01C')} />;
    case 'COUSR02C':
      return (
        <UserUpdateScreen userId={selectedUserId} onExit={() => setScreen('COADM01C')} />
      );
    case 'COUSR03C':
      return (
        <UserDeleteScreen userId={selectedUserId} onExit={() => setScreen('COADM01C')} />
      );
    case 'COPAUS0C':
      return (
        <AuthorizationSummaryScreen
          accountId={selectedAuthorization?.accountId ?? null}
          onExit={() => {
            setSelectedAuthorization(null);
            menu();
          }}
          onSelect={(accountId, authKey) => {
            setSelectedAuthorization({ accountId, authKey });
            setScreen('COPAUS1C');
          }}
        />
      );
    case 'COPAUS1C':
      return (
        <AuthorizationDetailScreen
          accountId={selectedAuthorization?.accountId ?? 0}
          authKey={selectedAuthorization?.authKey ?? ''}
          onExit={() => setScreen('COPAUS0C')}
        />
      );
    case 'COCRDLIC':
      return (
        <CardListScreen
          onExit={menu}
          onView={(cardAccountId, cardNumber) => {
            setSelectedCard({ accountId: cardAccountId, cardNumber });
            setScreen('COCRDSLC');
          }}
          onUpdate={(cardAccountId, cardNumber) => {
            setSelectedCard({ accountId: cardAccountId, cardNumber });
            setScreen('COCRDUPC');
          }}
        />
      );
    case 'COCRDSLC':
      return (
        <CardViewScreen
          accountId={selectedCard?.accountId ?? null}
          cardNumber={selectedCard?.cardNumber ?? null}
          onExit={() => {
            setSelectedCard(null);
            menu();
          }}
        />
      );
    case 'COCRDUPC':
      return (
        <CardUpdateScreen
          accountId={selectedCard?.accountId ?? null}
          cardNumber={selectedCard?.cardNumber ?? null}
          onExit={() => {
            setSelectedCard(null);
            menu();
          }}
        />
      );
    case 'COTRN00C':
      return (
        <TransactionListScreen
          onExit={menu}
          onSelect={(tranId) => {
            setSelectedTranId(tranId);
            setScreen('COTRN01C');
          }}
        />
      );
    case 'COTRN01C':
      return (
        <TransactionViewScreen
          tranId={selectedTranId}
          onExit={() => {
            setSelectedTranId(null);
            menu();
          }}
        />
      );
    case 'COTRN02C':
      return <TransactionAddScreen onExit={menu} />;
    case 'CORPT00C':
      return <ReportRequestScreen onExit={menu} />;
    case 'COBIL00C':
      return <BillPaymentScreen onExit={menu} />;
    case 'COADM01C':
      return (
        <AdminMenuScreen onSelect={(program) => setScreen(program as Screen)} onSignOff={signOff} />
      );
    default:
      return (
        <MenuScreen
          onSelect={(program) => setScreen(program as Screen)}
          onSignOff={signOff}
        />
      );
  }
}
