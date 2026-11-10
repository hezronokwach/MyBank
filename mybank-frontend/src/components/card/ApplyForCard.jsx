import { useState } from 'react';
import api from '../../api/axiosInstance';
import { useNotification } from '../notificationContext';
import { describeAccount, errorMessage, formatMoney, usableAccounts } from './cardHelpers';

// "Apply for a virtual card": pick the account that pays the fee and type its PIN.
// rejectedCard is the last card if an admin rejected it, so we can say why.
const ApplyForCard = ({ accounts, fee, rejectedCard, onApplied }) => {
    let feeAmount = 0;
    let feeCurrency = 'KES';
    if (fee) {
        feeAmount = fee.fee;
        feeCurrency = fee.currency;
    }

    const options = usableAccounts(accounts, feeCurrency);
    const [accountNumber, setAccountNumber] = useState('');
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const { notify } = useNotification();

    // The first usable account is chosen until the customer picks another
    let chosenAccount = accountNumber;
    if (chosenAccount === '' && options.length > 0) {
        chosenAccount = options[0].accountNumber;
    }

    const handleApply = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            await api.post('/cards', { accountNumber: chosenAccount, pin: pin });
            notify(`You applied for a card. ${formatMoney(feeAmount, feeCurrency)} was taken from •••${chosenAccount.slice(-4)}.`);
            onApplied();
        } catch (error) {
            notify(errorMessage(error, 'We could not send your application. Please try again.'), 'error');
        } finally {
            setPin('');
            setIsSubmitting(false);
        }
    };

    return (
        <section className="card card-panel">
            {rejectedCard && (
                <div className="card-notice card-notice--warning">
                    <strong>Your last application was not approved</strong>
                    <span>{rejectedCard.rejectionReason}</span>
                    <span>The {formatMoney(rejectedCard.feePaid, rejectedCard.currency)} fee was refunded to {rejectedCard.linkedAccount}.</span>
                </div>
            )}

            <p className="eyebrow">Virtual card</p>
            <h2 className="card-panel__title">Get a MyBank card</h2>
            <p className="card-panel__text">
                A card with its own balance. Move money onto it from your account, and back whenever you like.
                It costs <strong>{formatMoney(feeAmount, feeCurrency)}</strong>, taken when you apply. If it is not approved, you get the fee back.
            </p>

            {options.length === 0 && (
                <p className="card-notice">You need an active current or savings account in {feeCurrency} to pay for a card.</p>
            )}

            {options.length > 0 && (
                <form className="inline-form" onSubmit={handleApply}>
                    <label htmlFor="apply-account">Pay the fee from</label>
                    <select id="apply-account" value={chosenAccount} onChange={(e) => setAccountNumber(e.target.value)}>
                        {options.map((account) => (
                            <option key={account.accountNumber} value={account.accountNumber}>{describeAccount(account)}</option>
                        ))}
                    </select>
                    <label htmlFor="apply-pin">PIN of that account</label>
                    <input id="apply-pin" type="password" inputMode="numeric" autoComplete="off" maxLength="4"
                        value={pin} onChange={(e) => setPin(e.target.value)} required />
                    <button type="submit" className="button button--primary" disabled={isSubmitting || pin.length < 4}>
                        {isSubmitting ? 'Applying…' : 'Apply and pay ' + formatMoney(feeAmount, feeCurrency)}
                    </button>
                </form>
            )}
        </section>
    );
};

export default ApplyForCard;
