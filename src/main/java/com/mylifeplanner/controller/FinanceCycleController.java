package com.mylifeplanner.controller;

import com.mylifeplanner.controller.support.CycleSettingsForm;
import com.mylifeplanner.controller.support.PaymentForm;
import com.mylifeplanner.model.FundingSource;
import com.mylifeplanner.service.AccountService;
import com.mylifeplanner.service.FinanceCycleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;

@Controller
@RequestMapping("/finances/cycle")
public class FinanceCycleController {
    private final FinanceCycleService cycles;
    private final AccountService accounts;

    public FinanceCycleController(FinanceCycleService cycles, AccountService accounts) {
        this.cycles = cycles;
        this.accounts = accounts;
    }

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {
        LocalDate today = LocalDate.now();
        var overview = cycles.overview(date == null ? today : date, today);
        var user = accounts.currentUser();
        model.addAttribute("overview", overview);
        model.addAttribute("today", today);
        model.addAttribute("payday", user.getPayday());
        model.addAttribute("fundingSources", FundingSource.values());
        model.addAttribute("historical", overview.cycle().end().isBefore(today));
        model.addAttribute("activePage", "cycle");
        if (!model.containsAttribute("paymentForm")) model.addAttribute("paymentForm", new PaymentForm());
        if (!model.containsAttribute("settingsForm")) {
            CycleSettingsForm settings = new CycleSettingsForm();
            settings.setPayday(user.getPayday());
            settings.setOpeningBalance(user.getFinanceOpeningBalance());
            settings.setOpeningDate(user.getFinanceOpeningDate());
            model.addAttribute("settingsForm", settings);
        }
        return "finances/cycle";
    }

    @PostMapping("/settings")
    public String settings(@Valid @ModelAttribute("settingsForm") CycleSettingsForm form, BindingResult errors,
                           Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("openCycleModal", "cycleSettingsModal");
            return page(null, model);
        }
        cycles.configure(form);
        flash.addFlashAttribute("actionSuccess", "Tu ciclo y saldo inicial se actualizaron.");
        return "redirect:/finances/cycle";
    }

    @PostMapping("/payments")
    public String create(@Valid @ModelAttribute("paymentForm") PaymentForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("openCycleModal", "newPaymentModal");
            return page(null, model);
        }
        cycles.create(form);
        flash.addFlashAttribute("actionSuccess", form.getInstallments() == 1 ? "Pago pendiente creado."
                : "Se crearon " + form.getInstallments() + " cuotas mensuales pendientes.");
        return "redirect:/finances/cycle";
    }

    @GetMapping("/payments/{id}/edit")
    public String editPage(@PathVariable Long id, Model model) {
        var payment = cycles.find(id);
        if (payment.isPaid()) throw new IllegalArgumentException("Primero anula el pago para corregir esta cuota.");
        if (!model.containsAttribute("paymentForm")) {
            PaymentForm form = new PaymentForm();
            form.setDescription(payment.getDescription());
            form.setCreditor(payment.getCreditor());
            form.setAmount(payment.getAmount());
            form.setDueDate(payment.getDueDate());
            form.setFundingSource(payment.getFundingSource());
            model.addAttribute("paymentForm", form);
        }
        model.addAttribute("paymentId", id);
        model.addAttribute("fundingSources", FundingSource.values());
        model.addAttribute("activePage", "cycle");
        return "finances/payment-edit";
    }

    @PostMapping("/payments/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("paymentForm") PaymentForm form,
                         BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return editPage(id, model);
        cycles.update(id, form);
        flash.addFlashAttribute("actionSuccess", "Se actualizó esta cuota. Las demás conservan sus datos.");
        return "redirect:/finances/cycle";
    }

    @PostMapping("/payments/{id}/pay")
    public String pay(@PathVariable Long id,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
                      RedirectAttributes flash) {
        boolean created = cycles.pay(id, paymentDate);
        flash.addFlashAttribute("actionSuccess", created ? "Pago confirmado. Su gasto ya figura en Movimientos."
                : "Este pago ya estaba registrado. No se creó otro gasto.");
        return "redirect:/finances/cycle";
    }

    @PostMapping("/payments/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes flash) {
        cycles.reopen(id);
        flash.addFlashAttribute("actionSuccess", "La cuota vuelve a estar pendiente y se retiró su gasto asociado.");
        return "redirect:/finances/cycle";
    }

    @PostMapping("/payments/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        cycles.delete(id);
        flash.addFlashAttribute("actionSuccess", "Se eliminó esta cuota pendiente.");
        return "redirect:/finances/cycle";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(IllegalArgumentException error, RedirectAttributes flash) {
        flash.addFlashAttribute("cycleError", error.getMessage());
        return "redirect:/finances/cycle";
    }
}
