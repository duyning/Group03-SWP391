/*
 * Created on 2026-07-14: Shared collapsible admin sidebar behavior.
 * Created by: NinhDD - HE186113
 */
(function () {
    const STORAGE_KEY = "cineflow.adminSidebarCollapsed";

    function setCollapsed(collapsed) {
        document.body.classList.toggle("admin-sidebar-collapsed", collapsed);
        document.querySelectorAll(".admin-layout").forEach((layout) => {
            layout.classList.toggle("sidebar-collapsed", collapsed);
        });
        document.querySelectorAll(".sidebar-toggle").forEach((button) => {
            button.setAttribute("aria-expanded", String(!collapsed));
        });
    }

    function initSidebarToggle() {
        const collapsed = localStorage.getItem(STORAGE_KEY) === "true";
        setCollapsed(collapsed);

        document.querySelectorAll(".sidebar-toggle").forEach((button) => {
            if (button.dataset.sidebarToggleBound === "true") {
                return;
            }
            button.dataset.sidebarToggleBound = "true";
            button.addEventListener("click", () => {
                const nextCollapsed = !document.body.classList.contains("admin-sidebar-collapsed");
                localStorage.setItem(STORAGE_KEY, String(nextCollapsed));
                setCollapsed(nextCollapsed);
            });
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initSidebarToggle);
    } else {
        initSidebarToggle();
    }
})();
