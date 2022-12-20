# NeoCompounder

## Introduction

`NEO` is a native asset on the Neo blockchain that allows holders to claim GAS rewards in exchange for participating in Neo Governance through voting.


`bNEO` is a token that encapsulates the voting process and allows holders to maximize their voting rewards without worrying about periodically casting a vote for a different candidate.
`1.00000000 bNEO` can be exchanged for `1 NEO` at any time.


NeoCompounder introduces a new token `cNEO` that takes the `GAS` rewards from voting and compounds it into more underlying `bNEO`, with the end result being that each `cNEO` token becomes worth more and more `bNEO` over time.
Users can mint new `cNEO` at any time by depositing `NEO` or `bNEO` in the `cNEO` contract and minting the equivalent value of `cNEO` tokens.
They can later burn their `cNEO` tokens to redeem the underlying `NEO` or `bNEO`, which will be a greater quantity due to the `GAS` compounding.

---

## Contract Hash

### Testnet

Contract Address: `NQuumFouScN5pjNFcVA2SVSmDjdydoCaib`

Script Hash: `0xcc2ca40f55790a77a6813c7578a5920ca4c5d536`

### Mainnet

Contract Address: `Not yet deployed`

Script Hash: `Not yet deployed`

---

## How Does cNEO Work?

### Initial State
`cNEO` starts out with a `totalSupply == 0`.
At this point, we also have that `bneoReserves == 0`

### First Mint
When a user decides to mint `cNEO`, they can transfer either `NEO` or `bNEO` to the `cNEO` contract address.
If the user transfers `x bNEO`, the contract mints `x cNEO` for the user and updates `bneoReserves` to `x`.
If the user transfers `x NEO`, the contract mints `x bNEO` by locking up this `NEO`, mints `x cNEO` for the user and updates `bneoReserves` to `x`.

### Compounding
Compounding follows the following steps:
1. A caller invokes the `compound` method. This is only callable once every `compoundPeriod`.
2. The `cNEO` contract claims `GAS` for all of its underlying `bNEO` reserves.
3. The `cNEO` contract sets aside `feePercent GAS` for its operations.
4. The `cNEO` contract takes the remaining `GAS` and swaps it for `bNEO` on Flamingo.
5. The `cNEO` contract takes the remaining `GAS` and swaps it for `y bNEO` on the Flamingo `bNEO-GAS` pool.
6. The `cNEO` contract updates `bneoReserves == x + y` 
7. The `cNEO` contract rewards the caller of `compound` with enough GAS to fund the invocation fees and a little bit extra.

An important consideration is that the call to `compound` is paid for *using the GAS balance of the `cNEO` contract*!
The invoker risks simply receives a reward for their service.

### Burn
After the first mint and comounding, we now have `x cNEO` backed by `x + y bNEO`.
When a user burns `cNEO`, they will now receive `(x + y) / x bNEO` for every `cNEO` burned.
For example, if `x == 10` and `y == 1`, then each `cNEO` can be burned for `1.1 bNEO`.
The user can also burn `cNEO` to receive `NEO`, but will need to make a separate call to retrieve any fractional quantities in `bNEO`.

### Additional Mint
We still have the ratio of `x cNEO` to `x + y bNEO`.
Any new `cNEO` mints will now be minted in the ratio of `x / (x + y) cNEO` per `x bNEO`.
For example, if `x == 10` and `y == 1`, then each `bNEO` will now mint `1 / 1.1 cNEO`.

### Max Supply

Initially, `cNEO` will have a `maxSupply` of `1000000.00000000`.
This is another mechanism to prevent attackers from profiting from the `compound` call, as this limits the size of the `GAS` swap.

---

## User Actions

### Mint

#### Mint From bNEO

A user can mint `cNEO` simply by transferring `bNEO` to the `cNEO` contract with no parameters.

```
bNEO.transfer(account, cNEO, quantity, null), where

bNEO      is the NeoBurger contract
account   is the address that wishes to mint cNEO
cNEO      is the NeoCompounder contract
quantity  is the quantity of the collateral token to be deposited
```

#### Mint From NEO

A user can also mint `cNEO` simply by transferring `NEO` to the `cNEO` contract with no parameters.

```
NEO.transfer(account, cNEO, quantity, null), where

NEO       is the the NeoToken contract
account   is the address that wishes to mint cNEO
cNEO      is the NeoCompounder contract
quantity  is the quantity of the collateral token to be deposited
```

### Burn

#### Burn For bNEO

A user can burn `cNEO` to retrieve `bNEO` by transferring `cNEO` to the `cNEO` contract with no paramers.

```
cNEO.transfer(account, cNEO, quantity, null), where

cNEO      is the NeoCompounder contract
account   is the address that wishes to mint cNEO
quantity  is the quantity of the collateral token to be deposited
```

#### Burn For NEO *8(not implemented)**

We have decided not to support burning `cNEO` directly for `NEO` in the `cNEO` contract.
The primary reason is that it is difficult to ensure that this works at all times without making the contract fragile.
When the underlying `bNEO` is burned for `NEO`, the `NEO` can be transferred from any one of the NeoBurger agents.
Since agents can be added or removed at any time, it is difficult to ensure that the incoming `NEO` is from a NeoBurger agent.
Web applications can still support burning `cNEO` for `NEO` by using `invokeMulti`.

### Compound

A user can compound the underlying `bNEO` reserves of `cNEO` by calling `compound` with their wallet address.

```
cNEO.compound(account), where

cNEO      is the NeoCompounder contract
account   is the address of the transaction signer
```

Because the call to `compound` must contain the `cNEO` contract as a valid witness for the `GAS` transfer to the `bNEO-GAS` pool
and is expected to use GAS from the contract to fund the transaction, the signers and witnesses must be ordered as `(cNEO, account)`.

---

## Events

  | Event Name | Arguments |
  | ------ | ----------- |
  | Mint        | `(account, mintQuantity)` |
  | Burn        | `(account, burnQuantity)` |
  | Transfer    | `(from, to, transferQuantity)` |
  | Compound    | `(account, gasQuantity, bneoQuantity, treasuryCut)` |
  | TopUpGas    | `(account, topUpQuantity)` |
  | WithdrawGas | `(account, withdrawQuantity)` |

---

## Design Considerations

### Incentivized Compounding

In order to decentralize the operations as much as possible, `cNEO` allows anyone to call the `compound` method, provided that `compoundPeriod` has elapsed since the previous call.
The contract sends `GAS` back to the caller to make the call profitable.
`cNEO` funds these compounding calls by taking `feePercent` of the `GAS` claimed at every call of `compound` in its treasury.

### Compound Period

The `compoundPeriod` is adjustable and will be continuously tweaked to ensure that the amount of `GAS` swapped every period is not very large.
This is to ensure that it will not be profitable for an attacker to move the `bNEO-GAS` pool in anticipation of the `compound` call on the next block.
Initially, `compoundPeriod` will be set to `1 week`.
This can be set to `1 day` or even smaller depending on the eventual growth of `cNEO`.
