import type { ReactNode } from 'react';

/**
 * The header lines every CardDemo map carries: the two titles of COTTL01Y, the transaction and
 * program name and the date/time fields. The terminal used fixed screen positions; here they are a
 * flex header, which is a delivery-mechanism change only.
 */
export function ScreenFrame({
  transaction,
  program,
  title,
  children,
}: {
  transaction: string;
  program: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <main className="screen">
      <div className="screen__titles">
        <span>AWS Mainframe Modernization</span>
        <span>CardDemo</span>
      </div>
      <div className="screen__transaction">
        <span>Tran: {transaction}</span>
        <h1 style={{ fontSize: '1rem', margin: 0 }}>{title}</h1>
        <span>Prog: {program}</span>
      </div>
      {children}
    </main>
  );
}
