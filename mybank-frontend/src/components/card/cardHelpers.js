// Small helpers shared by the card screens

export function formatMoney(amount, currency) {
    return new Intl.NumberFormat('en-KE', {
        style: 'currency', currency: currency || 'KES', minimumFractionDigits: 2,
    }).format(amount || 0);
}

// 3 and 2029 become "03/29"
export function formatExpiry(month, year) {
    if (!month || !year) {
        return '--/--';
    }
    let monthText = String(month);
    if (month < 10) {
        monthText = '0' + month;
    }
    return monthText + '/' + String(year).slice(-2);
}

// "9991234567890123" becomes "9991 2345 6789 0123"
export function groupCardNumber(number) {
    const groups = [];
    for (let i = 0; i < number.length; i = i + 4) {
        groups.push(number.substring(i, i + 4));
    }
    return groups.join(' ');
}

// The accounts that can be used with a card: CURRENT or SAVINGS, ACTIVE, in the card's currency.
// Fixed accounts are never listed.
export function usableAccounts(accounts, currency) {
    const usable = [];
    for (const account of accounts) {
        const rightType = account.type === 'CURRENT' || account.type === 'SAVINGS';
        if (rightType && account.status === 'ACTIVE' && account.currency === currency) {
            usable.push(account);
        }
    }
    return usable;
}

// "•••4821 · Current · KES 1,500.00"
export function describeAccount(account) {
    const type = account.type.charAt(0) + account.type.slice(1).toLowerCase();
    return '•••' + account.accountNumber.slice(-4) + ' · ' + type + ' · ' + formatMoney(account.balance, account.currency);
}

// The error message from the API, or a friendly default
export function errorMessage(error, fallback) {
    if (error.response && error.response.data && error.response.data.message) {
        return error.response.data.message;
    }
    return fallback;
}
