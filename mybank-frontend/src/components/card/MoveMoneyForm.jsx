import { useState } from 'react';
import api from '../../api/axiosInstance';
import { useNotification } from '../notificationContext';
import { describeAccount, errorMessage, formatMoney, usableAccounts } from './cardHelpers';

// One form for both directions:
//   direction "topUp":    account → card ("Add money")
//   direction "withdraw": card → account ("Move to account")
const MoveMoneyForm = ({ card, accounts, direction, onMoved }) => {
    const options = usableAccounts(accounts, card.currency);
    const [accountNumber, setAccountNumber] = useState('');
    const [amount, setAmount] = useState('');
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const { notify } = useNotification();

    const isTopUp = direction === 'topUp';

    let chosenAccount = accountNumber;
    if (chosenAccount === '' && options.length > 0) {
        chosenAccount = options[0].accountNumber;
    }

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        // A new key for every press of the button: if the same request is sent twice, the money moves once
        const headers = { 'Idempotency-Key': crypto.randomUUID() };
        try {
            let response;
            if (isTopUp) {
                response = await api.post(`/cards/${card.id}/top-up`,
                    { fromAccountNumber: chosenAccount, amount: amount, pin: pin }, { headers: headers });
                notify(`${formatMoney(amount, card.currency)} added to your card.`);
            } else {
                response = await api.post(`/cards/${card.id}/withdraw`,
                    { toAccountNumber: chosenAccount, amount: amount, pin: pin }, { headers: headers });
                notify(`${formatMoney(amount, card.currency)} moved to •••${chosenAccount.slice(-4)}.`);
            }
            setAmount('');
            onMoved(response.data);
        } catch (error) {
            notify(errorMessage(error, 'We could not move the money. Please try again.'), 'error');
        } finally {
            setPin('');
            setIsSubmitting(false);
        }
    };

    if (options.length === 0) {
        return <p className="card-notice">You need an active current or savings account in {card.currency} to move money.</p>;
    }

    let accountLabel = 'To account';
    let buttonText = 'Move to account';
    if (isTopUp) {
        accountLabel = 'From account';
        buttonText = 'Add to card';
    }

    return (
        <form className="inline-form" onSubmit={handleSubmit}>
            <label htmlFor={`${direction}-account`}>{accountLabel}</label>
            <select id={`${direction}-account`} value={chosenAccount} onChange={(e) => setAccountNumber(e.target.value)}>
                {options.map((account) => (
                    <option key={account.accountNumber} value={account.accountNumber}>{describeAccount(account)}</option>
                ))}
            </select>

            <label htmlFor={`${direction}-amount`}>Amount ({card.currency})</label>
            <input id={`${direction}-amount`} type="number" min="0.01" step="0.01" value={amount}
                onChange={(e) => setAmount(e.target.value)} required />
            {!isTopUp && <small className="inline-form__hint">On the card: {formatMoney(card.balance, card.currency)}</small>}

            <label htmlFor={`${direction}-pin`}>PIN</label>
            <input id={`${direction}-pin`} type="password" inputMode="numeric" autoComplete="off" maxLength="4"
                value={pin} onChange={(e) => setPin(e.target.value)} required />

            <button type="submit" className="button button--primary" disabled={isSubmitting || pin.length < 4 || amount === ''}>
                {isSubmitting ? 'Moving…' : buttonText}
            </button>
        </form>
    );
};

export default MoveMoneyForm;
