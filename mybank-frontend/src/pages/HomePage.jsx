import { Link } from 'react-router-dom';

function HomePage() {
  return (
    <div className="home-page">
      <header className="home-nav">
        <Link to="/" className="brand">MyBank</Link>
        <div className="home-nav__links">
          <Link to="/login">Sign in</Link>
          <Link to="/register" className="home-nav__register">Open an account</Link>
        </div>
      </header>

      <main className="home-main">
        <section className="home-hero">
          <div>
            <p className="eyebrow">Simple, secure banking</p>
            <h1>Banking that keeps up with your day.</h1>
            <p className="home-hero__copy">Manage your money, move funds, and stay in control from one straightforward account dashboard.</p>
            <div className="home-actions">
              <Link to="/register" className="button button--primary">Open an account</Link>
              <Link to="/login" className="button button--secondary">Sign in</Link>
            </div>
          </div>
          <aside className="balance-preview" aria-label="Account overview preview">
            <div className="balance-preview__top"><span>MyBank account</span><span className="balance-preview__mark">M</span></div>
            <p className="balance-preview__label">Available balance</p>
            <p className="balance-preview__amount">KES 24,800.00</p>
            <div className="balance-preview__line"><span>Everyday account</span><span>••• 4082</span></div>
          </aside>
        </section>

        <section className="home-notice">
          <div className="home-notice__icon" aria-hidden="true">✓</div>
          <div>
            <h2>Account verification</h2>
            <p>New accounts are created in a pending state. An administrator will review and activate your account before banking features become available.</p>
          </div>
        </section>

        <section className="home-details">
          <div><span className="home-details__number">01</span><h2>One clear view</h2><p>See account balance, status, and quick actions together on your dashboard.</p></div>
          <div><span className="home-details__number">02</span><h2>Move money easily</h2><p>Deposit, withdraw, or transfer funds through a focused and secure flow.</p></div>
          <div><span className="home-details__number">03</span><h2>Admin review</h2><p>Account activation and account management remain protected by role-based access.</p></div>
        </section>

        <details className="admin-credentials">
          <summary>Testing the admin experience?</summary>
          <div>
            <p>Use these development credentials only when evaluating administrative features.</p>
            <code>admin@mybank.com</code>
            <code>adminpassword</code>
          </div>
        </details>
      </main>
    </div>
  );
}

export default HomePage;
